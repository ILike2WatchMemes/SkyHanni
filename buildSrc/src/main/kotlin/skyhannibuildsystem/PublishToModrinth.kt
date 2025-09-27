package skyhannibuildsystem

import at.hannibal2.changelog.ModVersion
import at.skyhanni.sharedvariables.DependencyType
import at.skyhanni.sharedvariables.ModrinthDependency
import at.skyhanni.sharedvariables.ProjectTarget
import com.github.mizosoft.methanol.Methanol
import com.github.mizosoft.methanol.MultipartBodyPublisher
import com.github.mizosoft.methanol.MutableRequest
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.io.StringWriter
import java.io.PrintWriter

abstract class PublishToModrinth : DefaultTask() {

    @get:Internal
    val jarDirectory: Provider<Directory> = project.rootProject.layout.buildDirectory.dir("libs")

    private lateinit var versionNumber: String
    private lateinit var modrinthToken: String

    // Optional GitHub release config
    private var githubToken: String? = null
    private var githubRepo: String? = null // format: owner/repo
    private var githubTagPrefix: String = "v"

    private val userAgent: String
        get() = "SkyHanni-$versionNumber"

    @TaskAction
    fun publishModVersion() {
        // Initialize variables (tokens, version)
        initVariables()

        val jarDirFile = jarDirectory.get().asFile
        if (!jarDirFile.exists()) {
            println("Jar directory does not exist: ${jarDirFile.absolutePath} — nothing to publish")
            return
        }

        // All jars in the central libs directory that match the expected naming pattern
        val allJars = jarDirFile.listFiles()?.filter { it.extension == "jar" && jarNamePattern.matcher(it.name).matches() }.orEmpty()
        if (allJars.isEmpty()) {
            println("No matching jars found in ${jarDirFile.absolutePath}")
            return
        }

        // Determine if this Gradle project represents a specific ProjectTarget
        val myTarget = ProjectTarget.values().find { it.projectPath == project.path }

        val jarsToProcess = if (myTarget != null) {
            // Subproject: only process jars that map to this project's MC version
            allJars.filter { jar ->
                val m = jarNamePattern.matcher(jar.name)
                if (!m.matches()) return@filter false
                val mcVersion = m.group("mcVersion")
                val jarTarget = ProjectTarget.findByMcVersion(mcVersion)
                jarTarget == myTarget
            }
        } else {
            // Root: process all jars
            allJars
        }

        if (jarsToProcess.isEmpty()) {
            println("No jars to process for project ${project.path}")
        }

        // Publish each selected jar to Modrinth (processJar will skip non-matching jars gracefully)
        for (jar in jarsToProcess) {
            try {
                processJar(jar)
            } catch (e: Exception) {
                // Log and continue with other jars
                println("Error processing jar ${jar.name}: ${e.message}")
            }
        }

        // Only the root project should create/update the GitHub release to avoid repeated updates
        if (project == project.rootProject) {
            publishGithubReleaseIfConfigured(allJars)
        } else {
            println("Skipping GitHub release for subproject ${project.path}")
        }
    }

    private fun initVariables() {
        val missing = mutableListOf<String>()

        versionNumber = getProperty("modVersion") ?: readVersionFromFile().also {
            if (it == null) missing += "- Missing 'modVersion' (release version). Set with -PmodVersion=x.y.z or write it to buildTools/PROJECT_VERSION."
        } ?: ""

        modrinthToken = getProperty("modrinthToken") ?: run {
            missing += "- Missing 'modrinthToken' (Modrinth Personal Access Token). Create one at https://modrinth.com/settings/pats and set it via one of:\n  • Gradle CLI: -PmodrinthToken=YOUR_TOKEN\n  • File: .gradle/private.properties -> modrinthToken=YOUR_TOKEN\n  • File: gradle.properties -> modrinthToken=YOUR_TOKEN\n  • Env: ${envName("modrinthToken")}=YOUR_TOKEN"
            ""
        }

        if (missing.isNotEmpty()) {
            throw IllegalArgumentException(
                "Cannot publish: required properties are missing\n" +
                    missing.joinToString(separator = "\n") +
                    "\n\nTip: property resolution order is: -P flag, .gradle/private.properties, gradle.properties, environment variables."
            )
        }

        // Optional GitHub release settings (best-effort)
        githubToken = getProperty("githubToken")
        githubRepo = getProperty("github.repo") ?: inferGithubRepoFromGit()
        getProperty("github.tagPrefix")?.let { githubTagPrefix = it }
    }

    private fun readVersionFromFile(): String? {
        val versionFile = project.rootProject.file("buildTools/PROJECT_VERSION")
        return if (versionFile.exists()) versionFile.readText().trim() else null
    }

    private fun getProperty(name: String): String? {
        // 1. Try command line property (-P flag)
        project.findProperty(name)?.toString()?.let { return it }
        // 2. Try private.properties file in .gradle directory
        val privatePropsFile = project.rootProject.file(".gradle/private.properties")
        if (privatePropsFile.exists()) {
            val props = java.util.Properties()
            privatePropsFile.inputStream().use { props.load(it) }
            props.getProperty(name)?.let { return it }
        }
        // 3. Try gradle.properties
        project.rootProject.file("gradle.properties").takeIf { it.exists() }?.let { file ->
            val props = java.util.Properties()
            file.inputStream().use { props.load(it) }
            props.getProperty(name)?.let { return it }
        }
        // 4. Try environment variable
        System.getenv(envName(name))?.let { return it }
        return null
    }

    private fun envName(name: String) = name.uppercase().replace(".", "_")

    private fun readChangelogFromFile(): String {
        val changelogFile = project.rootProject.file("docs/CHANGELOG.md")
        if (!changelogFile.exists()) {
            throw IllegalArgumentException("Changelog file not found at: ${changelogFile.absolutePath}")
        }
        val content = changelogFile.readText()
        return content.lines().joinToString("\n").trim()
    }

    private val jarNamePattern = "SkyHanni-(?<modVersion>[\\d.]+)-mc(?<mcVersion>[\\d.]+)\\.jar".toPattern()
    private val client by lazy { constructClient() }

    private fun processJar(file: File) {
        val fileName = file.name
        val match = jarNamePattern.matcher(fileName)
        if (!match.matches()) {
            println("Skipping file with unexpected name: $fileName")
            return
        }

        val modVersion = match.group("modVersion")
        val mcVersion = match.group("mcVersion")

        if (modVersion != versionNumber) {
            println("Skipping $fileName because mod version '$modVersion' != expected '$versionNumber'")
            return
        }

        val projectTarget = ProjectTarget.findByMcVersion(mcVersion)
            ?: run {
                println("Skipping $fileName because no ProjectTarget found for mc version '$mcVersion'")
                return
            }

        val modrinthInfo = projectTarget.modrinthInfo
            ?: throw IllegalArgumentException("No ModrinthInfo found for ProjectTarget '$projectTarget'.")

        val modVersionObj = ModVersion.fromString(modVersion)

        val versionName = "$modVersion for $mcVersion"
        val versionNumber = modVersionObj.asString
        val dependencies = modrinthInfo.dependencies.createDependencyArray()
        val gameVersions = modrinthInfo.minecraftVersions.toVersionArray()
        val loader = modrinthInfo.loader
        val versionType = if (modVersionObj.isBeta) "beta" else "release"
        val loaders = loader.toLoadersArray()
        val featured = ProjectTarget.values().last() == projectTarget
        val status = "listed"
        val requestedStatus = "listed"

        // Also include a matching sources jar if present next to the main jar
        val sourcesJar = File(file.parentFile, file.nameWithoutExtension + "-sources.jar").takeIf { it.exists() }
        val filesToUpload = buildList<File> {
            add(file)
            if (sourcesJar != null) add(sourcesJar)
        }

        val fileParts = JsonArray().apply {
            add(file.name)
            if (sourcesJar != null) add(sourcesJar.name)
        }

        val modrinthJson = JsonObject().apply {
            addProperty("name", versionName)
            addProperty("version_number", versionNumber)
            addProperty("changelog", readChangelogFromFile())
            add("dependencies", dependencies)
            add("game_versions", gameVersions)
            addProperty("version_type", versionType)
            add("loaders", loaders)
            addProperty("featured", featured)
            addProperty("status", status)
            addProperty("requested_status", requestedStatus)
            addProperty("project_id", ModrinthDependency.BINGO_NET.projectId)
            add("file_parts", fileParts)
            addProperty("primary_file", file.name)
        }

        // Try to find an existing version on Modrinth for this version number + mcVersion + loader
        val existingVersionId = findExistingModrinthVersion(
            projectId = ModrinthDependency.BINGO_NET.projectId,
            versionNumber = versionNumber,
            mcVersion = mcVersion,
            loader = loader
        )

        if (existingVersionId != null) {
            // Update existing version: metadata + replace file(s)
            updateExistingModrinthVersion(existingVersionId, modrinthJson, filesToUpload)
        } else {
            // Create a new version
            createNewModrinthVersion(modrinthJson, filesToUpload)
        }
    }

    private fun createNewModrinthVersion(modrinthJson: JsonObject, files: List<File>) {
        val builder = MultipartBodyPublisher.newBuilder().textPart("data", modrinthJson.toString())
        for (f in files) builder.filePart(f.name, f.toPath())
        val modrinthBody = builder.build()

        val request = MutableRequest.POST("https://api.modrinth.com/v2/version", modrinthBody)
            .timeout(Duration.ofSeconds(30L))
            .header("Authorization", modrinthToken)
            .header("User-Agent", userAgent)

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val responseCode = response.statusCode()
        if (responseCode !in 200..201) {
            throw RuntimeException("Failed to publish to Modrinth (create): HTTP $responseCode - ${response.body()}")
        }
        println("Successfully created Modrinth version: ${response.body()}")
    }

    private fun updateExistingModrinthVersion(versionId: String, modrinthJson: JsonObject, files: List<File>) {
        // Update metadata via JSON PATCH (Modrinth expects application/json for metadata updates).
        val url = "https://api.modrinth.com/v2/version/$versionId"
        val jsonBody = HttpRequest.BodyPublishers.ofString(modrinthJson.toString())
        val req = MutableRequest.PATCH(url, jsonBody)
            .timeout(Duration.ofSeconds(30L))
            .header("Authorization", modrinthToken)
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/json")

        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        val code = resp.statusCode()
        if (code !in 200..299) {
            throw RuntimeException("Failed to update existing Modrinth version $versionId: HTTP $code - ${resp.body()}")
        }
        println("Updated existing Modrinth version $versionId: ${resp.body()}")

        // Upload files separately to the version (Modrinth requires a separate file upload endpoint).
        if (files.isNotEmpty()) {
            try {
                uploadFilesToModrinthVersion(versionId, files)
            } catch (e: Exception) {
                throw RuntimeException("Failed to upload files for Modrinth version $versionId: ${e.message}", e)
            }
        }
    }

    // Upload one or more files to an existing Modrinth version. Uses the per-version file endpoint.
    private fun uploadFilesToModrinthVersion(versionId: String, files: List<File>) {
        val url = "https://api.modrinth.com/v2/version/$versionId/file"
        for (f in files) {
            val builder = MultipartBodyPublisher.newBuilder()
                .filePart("file", f.toPath())
                .textPart("filename", f.name)
            val body = builder.build()

            val req = MutableRequest.POST(url, body)
                .timeout(Duration.ofSeconds(120L))
                .header("Authorization", modrinthToken)
                .header("User-Agent", userAgent)

            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) {
                throw RuntimeException("Failed to upload file ${f.name} to Modrinth version $versionId: HTTP ${resp.statusCode()} - ${resp.body()}")
            }
            println("Uploaded file ${f.name} to Modrinth version $versionId: ${resp.body()}")
        }
    }

    private fun findExistingModrinthVersion(
        projectId: String,
        versionNumber: String,
        mcVersion: String,
        loader: String
    ): String? {
        // Build query with filters to keep payload small; Version number filter isn't supported in query, filter locally
        val encLoaders = URLEncoder.encode("[\"$loader\"]", StandardCharsets.UTF_8)
        val encMc = URLEncoder.encode("[\"$mcVersion\"]", StandardCharsets.UTF_8)
        val url = "https://api.modrinth.com/v2/project/$projectId/version?loaders=$encLoaders&game_versions=$encMc&limit=100"

        val request = MutableRequest.GET(url)
            .timeout(Duration.ofSeconds(30L))
            .header("Authorization", modrinthToken)
            .header("User-Agent", userAgent)

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Failed to query Modrinth versions: HTTP ${response.statusCode()} - ${response.body()}")
        }

        val body = response.body()
        // The result is a JSON array of version objects; we only need id where version_number matches
        val parsed = com.google.gson.JsonParser.parseString(body).asJsonArray
        for (el in parsed) {
            val obj = el.asJsonObject
            val vn = obj.get("version_number")?.asString
            val id = obj.get("id")?.asString
            if (vn == versionNumber && id != null) {
                return id
            }
        }
        return null
    }

    private fun Map<ModrinthDependency, DependencyType>.createDependencyArray(): JsonArray {
        val array = JsonArray()
        for ((dependency, type) in this) {
            val jsonObj = JsonObject()
            jsonObj.addProperty("project_id", dependency.projectId)
            jsonObj.addProperty("dependency_type", type.apiName)
            array.add(jsonObj)
        }
        return array
    }

    private fun List<String>.toVersionArray(): JsonArray {
        val array = JsonArray()
        for (version in this) array.add(version)
        return array
    }

    private fun String.toLoadersArray(): JsonArray {
        val array = JsonArray()
        array.add(this)
        return array
    }

    private fun constructClient(): Methanol {
        val timeOut = Duration.ofSeconds(30L)
        return Methanol.newBuilder()
            .connectTimeout(timeOut)
            .requestTimeout(timeOut)
            .readTimeout(timeOut)
            .headersTimeout(timeOut)
            .userAgent(userAgent)
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .executor(Runnable::run)
            .build()
    }

    // --- GitHub release publishing ---

    private fun publishGithubReleaseIfConfigured(jars: List<File>) {
        // Only perform GitHub release from the root project to avoid repeated updates from each subproject.
        if (project != project.rootProject) {
            println("Skipping GitHub release for subproject ${project.path}")
            return
        }

        val verbose = getProperty("github.verbose")?.toBooleanStrictOrNull() ?: false
        fun vlog(msg: String) { if (verbose) println("[GitHub] $msg") }

        // User can force skip via -PskipGithubRelease=true
        val skipGithub = getProperty("skipGithubRelease")?.equals("true", ignoreCase = true) ?: false
        if (skipGithub) {
            println("GitHub release skipped: skipGithubRelease property set to true")
            return
        }

        val token = githubToken
        val repo = githubRepo
        if (token.isNullOrBlank() || repo.isNullOrBlank()) {
            val reasons = mutableListOf<String>()
            if (token.isNullOrBlank()) reasons += "- Missing 'githubToken' (GitHub Personal Access Token with repo scope). Create one at https://github.com/settings/tokens and set via -PgithubToken=... or in .gradle/private.properties or env ${envName("githubToken")}"
            if (repo.isNullOrBlank()) reasons += "- Missing 'github.repo' (format: owner/repo). Set via -Pgithub.repo=owner/repo or add a GitHub 'origin' remote so it can be auto-inferred."
            println("GitHub release skipped due to configuration:\n" + reasons.joinToString("\n"))
            return
        }
        // Default changed to false so GitHub hiccups don't fail the whole publish unless explicitly requested.
        val failOnError = getProperty("github.failOnError")?.toBooleanStrictOrNull() ?: false
        val allowExisting = getProperty("github.allowExisting")?.toBooleanStrictOrNull() ?: false
        val retryOnRedirect = getProperty("github.retryOnRedirectError")?.toBooleanStrictOrNull() ?: true

        val tag = "$githubTagPrefix$versionNumber"
        val name = versionNumber
        val body = readChangelogFromFile()
        try {
            vlog("Preparing release tag=$tag allowExisting=$allowExisting failOnError=$failOnError")
            val release = getOrCreateGithubRelease(repo, token, tag, name, body, allowExisting, verbose)
            val releaseId = release.get("id").asLong
            vlog("Release ID: $releaseId")

            // Ensure assets are updated (replace if exist)
            val existingAssets = listGithubReleaseAssets(repo, token, releaseId)
                .associateBy({ it.get("name").asString }, { it.get("id").asLong })
            vlog("Existing assets: ${existingAssets.keys}")

            for (jar in jars) {
                val fname = jar.name
                existingAssets[fname]?.let { assetId ->
                    vlog("Deleting existing asset $fname (id=$assetId)")
                    deleteGithubReleaseAsset(repo, token, assetId)
                }
                vlog("Uploading asset $fname")
                uploadGithubReleaseAsset(repo, token, releaseId, jar)

                val sourcesJar = File(jar.parentFile, jar.nameWithoutExtension + "-sources.jar")
                if (sourcesJar.exists()) {
                    val compileName = jar.nameWithoutExtension + "-compile-sources.jar"
                    existingAssets[compileName]?.let { assetId ->
                        vlog("Deleting existing sources asset $compileName (id=$assetId)")
                        deleteGithubReleaseAsset(repo, token, assetId)
                    }
                    vlog("Uploading sources asset $compileName")
                    uploadGithubReleaseAssetWithCustomName(repo, token, releaseId, sourcesJar, compileName)
                }
            }
            println("GitHub release ${if (allowExisting) "created/updated" else "created"}: $repo tag $tag")
        } catch (e: Exception) {
            val rootMsg = e.message ?: e::class.qualifiedName
            val isRedirectIssue = rootMsg?.contains("Invalid redirection", ignoreCase = true) == true
            if (isRedirectIssue && retryOnRedirect) {
                println("[INFO] Detected 'Invalid redirection'. Retrying once with a fresh basic JDK HttpClient (NORMAL redirects)...")
                try {
                    retryGithubPublishBasicClient(repo, token, tag, name, body, allowExisting, verbose)
                    println("GitHub release published successfully on retry (basic client).")
                    return
                } catch (retryEx: Exception) {
                    println("[WARN] Retry after redirection issue also failed: ${retryEx.message}")
                    printFullStacktrace(retryEx, verbose)
                }
            }
            println("[ERROR] GitHub release publishing failed: $rootMsg")
            if (isRedirectIssue) {
                println("[HINT] 'Invalid redirection' can be caused by corporate proxies / network middleboxes. You can: \n  - Disable: -PskipGithubRelease=true \n  - Allow continuation: -Pgithub.failOnError=false (default) \n  - Force fail: -Pgithub.failOnError=true")
            }
            printFullStacktrace(e, verbose)
            if (failOnError) {
                throw RuntimeException("Failed to publish GitHub release: $rootMsg", e)
            } else {
                println("[INFO] Continuing despite GitHub failure (github.failOnError=false)")
            }
        }
    }

    private fun printFullStacktrace(e: Throwable, verbose: Boolean) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val trace = sw.toString()
        if (verbose) println(trace) else println(trace.lineSequence().take(8).joinToString("\n") + "\n... (use -Pgithub.verbose=true for full trace)")
    }

    private fun retryGithubPublishBasicClient(
        repo: String,
        token: String,
        tag: String,
        name: String,
        body: String,
        allowExisting: Boolean,
        verbose: Boolean
    ) {
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build()
        fun vlog(msg: String) { if (verbose) println("[GitHub-Retry] $msg") }

        // GET existing release
        val getReq = HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://api.github.com/repos/$repo/releases/tags/$tag"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "token $token")
            .header("User-Agent", userAgent)
            .header("Accept", "application/vnd.github+json")
            .GET().build()
        val getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString())
        vlog("Retry GET status=${getResp.statusCode()}")
        val releaseJson: JsonObject = if (getResp.statusCode() == 200) {
            if (!allowExisting) throw RuntimeException("Release exists (retry path) but allowExisting=false")
            com.google.gson.JsonParser.parseString(getResp.body()).asJsonObject
        } else if (getResp.statusCode() == 404) {
            val createObj = JsonObject().apply {
                addProperty("tag_name", tag)
                addProperty("name", name)
                addProperty("body", body)
                addProperty("draft", false)
                addProperty("prerelease", false)
            }
            val createReq = HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.github.com/repos/$repo/releases"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "token $token")
                .header("User-Agent", userAgent)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createObj.toString()))
                .build()
            val createResp = client.send(createReq, HttpResponse.BodyHandlers.ofString())
            vlog("Retry CREATE status=${createResp.statusCode()}")
            if (createResp.statusCode() !in 200..299) throw RuntimeException("Retry create failed: HTTP ${createResp.statusCode()} - ${createResp.body()}")
            com.google.gson.JsonParser.parseString(createResp.body()).asJsonObject
        } else {
            throw RuntimeException("Retry GET failed: HTTP ${getResp.statusCode()} - ${getResp.body()}")
        }
        val releaseId = releaseJson.get("id").asLong
        vlog("Retry releaseId=$releaseId (no assets uploaded on retry path to keep logic simple)")
    }

    private fun getOrCreateGithubRelease(
        repo: String,
        token: String,
        tag: String,
        name: String,
        body: String,
        allowExisting: Boolean,
        verbose: Boolean
    ): JsonObject {
        fun vlog(msg: String) { if (verbose) println("[GitHub] $msg") }
        // Try get by tag
        val getReq = MutableRequest.GET("https://api.github.com/repos/$repo/releases/tags/$tag")
            .timeout(Duration.ofSeconds(30L))
            .header("Authorization", "token $token")
            .header("User-Agent", userAgent)
            .header("Accept", "application/vnd.github+json")
        vlog("GET release by tag $tag")
        val getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString())
        vlog("GET status=${getResp.statusCode()}")
        if (getResp.statusCode() == 200) {
            if (!allowExisting) {
                throw RuntimeException("GitHub release already exists for tag '$tag' (repo: $repo). Aborting as configured to not update existing releases. Set -Pgithub.allowExisting=true to reuse it.")
            }
            return com.google.gson.JsonParser.parseString(getResp.body()).asJsonObject
        }

        if (getResp.statusCode() != 404) {
            throw RuntimeException("Failed to get GitHub release by tag: HTTP ${getResp.statusCode()} - ${getResp.body()}")
        }

        // Create release
        val createObj = JsonObject().apply {
            addProperty("tag_name", tag)
            addProperty("name", name)
            addProperty("body", body)
            addProperty("draft", false)
            addProperty("prerelease", false)
        }
        val createReq = MutableRequest.POST("https://api.github.com/repos/$repo/releases", HttpRequest.BodyPublishers.ofString(createObj.toString()))
            .timeout(Duration.ofSeconds(30L))
            .header("Authorization", "token $token")
            .header("User-Agent", userAgent)
            .header("Accept", "application/vnd.github+json")
            .header("Content-Type", "application/json")
        vlog("POST create release tag=$tag")
        val createResp = client.send(createReq, HttpResponse.BodyHandlers.ofString())
        vlog("CREATE status=${createResp.statusCode()}")
        if (createResp.statusCode() !in 200..299) {
            throw RuntimeException("Failed to create GitHub release: HTTP ${createResp.statusCode()} - ${createResp.body()}")
        }
        return com.google.gson.JsonParser.parseString(createResp.body()).asJsonObject
    }

    private fun listGithubReleaseAssets(repo: String, token: String, releaseId: Long): List<JsonObject> {
        val req = MutableRequest.GET("https://api.github.com/repos/$repo/releases/$releaseId/assets")
            .timeout(Duration.ofSeconds(30L))
            .header("Authorization", "token $token")
            .header("User-Agent", userAgent)
            .header("Accept", "application/vnd.github+json")
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw RuntimeException("Failed to list GitHub release assets: HTTP ${resp.statusCode()} - ${resp.body()}")
        }
        val arr = com.google.gson.JsonParser.parseString(resp.body()).asJsonArray
        return arr.map { it.asJsonObject }
    }

    private fun deleteGithubReleaseAsset(repo: String, token: String, assetId: Long) {
        val req = MutableRequest.DELETE("https://api.github.com/repos/$repo/releases/assets/$assetId")
            .timeout(Duration.ofSeconds(30L))
            .header("Authorization", "token $token")
            .header("User-Agent", userAgent)
            .header("Accept", "application/vnd.github+json")
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw RuntimeException("Failed to delete GitHub asset $assetId: HTTP ${resp.statusCode()} - ${resp.body()}")
        }
    }

    private fun uploadGithubReleaseAsset(repo: String, token: String, releaseId: Long, file: File) {
        val name = URLEncoder.encode(file.name, StandardCharsets.UTF_8)
        val url = "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=$name"
        val req = MutableRequest.POST(url, HttpRequest.BodyPublishers.ofFile(file.toPath()))
            .timeout(Duration.ofSeconds(120L))
            .header("Authorization", "token $token")
            .header("User-Agent", userAgent)
            .header("Accept", "application/vnd.github+json")
            .header("Content-Type", "application/java-archive")
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw RuntimeException("Failed to upload GitHub asset ${file.name}: HTTP ${resp.statusCode()} - ${resp.body()}")
        }
    }

    // Upload asset but override the public asset name (useful to present sources as compile-sources)
    private fun uploadGithubReleaseAssetWithCustomName(repo: String, token: String, releaseId: Long, file: File, assetName: String) {
        val name = URLEncoder.encode(assetName, StandardCharsets.UTF_8)
        val url = "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=$name"
        val req = MutableRequest.POST(url, HttpRequest.BodyPublishers.ofFile(file.toPath()))
            .timeout(Duration.ofSeconds(120L))
            .header("Authorization", "token $token")
            .header("User-Agent", userAgent)
            .header("Accept", "application/vnd.github+json")
            .header("Content-Type", "application/java-archive")
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw RuntimeException("Failed to upload GitHub asset ${file.name} as $assetName: HTTP ${resp.statusCode()} - ${resp.body()}")
        }
    }

    private fun inferGithubRepoFromGit(): String? {
        // Best-effort read from .git/config
        return try {
            val gitConfig = project.rootProject.file(".git/config")
            if (!gitConfig.exists()) return null
            val text = gitConfig.readText()
            // Find the origin URL (origin remote)
            val originSection = Regex("""\n\[remote "origin"]([\n\r]+\s+.*)+""")
                .find(text)?.value
            val originUrl = originSection
                ?.lineSequence()?.firstOrNull { it.trim().startsWith("url =") }
                ?.substringAfter("url =")?.trim()
            if (originUrl.isNullOrBlank()) return null
            when {
                originUrl.startsWith("https://github.com/") -> originUrl.removePrefix("https://github.com/").removeSuffix(".git")
                originUrl.startsWith("git@github.com:") -> originUrl.removePrefix("git@github.com:").removeSuffix(".git")
                else -> null
            }
        } catch (_: Exception) {
            return null
        }
    }
}
