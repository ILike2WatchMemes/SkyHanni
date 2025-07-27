package at.hannibal2.skyhanni.utils

import net.minecraft.client.Minecraft
import org.apache.http.client.ResponseHandler
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.math.BigInteger
import java.util.Random

object MojangUtils {
    /**
     * "Joining" a Server is a method to validate that someone is who they say they are.
     * This works by letting both the client and server to accept a server
     * and the server can then ask mojang whether they validate the User.
     */

    fun joinServer(server: String) {
        //#if MC < 1.16
        Minecraft.getMinecraft().sessionService.joinServer(
            Minecraft
                .getMinecraft()
                .session
                .profile,
            Minecraft.getMinecraft().session.token, server,
        )
        //#else
        //$$    MinecraftClient.getInstance().getSessionService().joinServer(
        //$$             MinecraftClient.getInstance().getGameProfile().getId(),
        //$$             MinecraftClient.getInstance().getSession().getAccessToken(),
        //$$             server,
        //$$         )
        //#endif
    }

    /**
     * Mojang Auth check
     *
     * @param username  MC Username. Case sensitive.
     * @param serverId
     * @param ipAddress allows to check against proxy. Not flawless.
     * @return
     */
    @JvmStatic
    fun verifyPlayer(username: String, serverId: String, ipAddress: String? = null): Boolean {
        try {
            val loginHash = serverId

            // Create an HTTP client
            val httpClient = HttpClients.createDefault()

            // Build the URL for the HTTP GET request
            val urlBuilder = StringBuilder("https://sessionserver.mojang.com/session/minecraft/hasJoined")
            urlBuilder.append("?username=").append(username)
            urlBuilder.append("&serverId=").append(loginHash)

            if (ipAddress != null && ipAddress.isNotEmpty()) {
                urlBuilder.append("&ip=").append(ipAddress)
            }

            // Create an HTTP GET request
            val request = HttpGet(urlBuilder.toString())

            // Response handler that automatically consumes the response entity
            val responseHandler = ResponseHandler { response ->
                EntityUtils.consume(response.entity)
                response.statusLine.statusCode == 200
            }

            // Execute the request using the new execute method with a response handler
            val isValid = httpClient.execute(request, responseHandler)

            // Close the HTTP client
            httpClient.close()

            return isValid
        } catch (e: Exception) {
            e.printStackTrace()
            return false // An error occurred
        }
    }

    fun generateClientRandom(): String {
        val r1 = Random()
        val r2 = Random(System.identityHashCode(Any()).toLong())
        val random1Bi = BigInteger(64, r1)
        val random2Bi = BigInteger(64, r2)
        val serverBi = random1Bi.xor(random2Bi)
        return serverBi.toString(16)
    }
}
