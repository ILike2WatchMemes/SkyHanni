package de.hype.bingonet.environment.packetconfig

import java.awt.Color
import java.time.Duration
import java.time.Instant
import de.hype.bingonet.shared.json.ColorSerializer
import de.hype.bingonet.shared.json.DurationSerializer
import de.hype.bingonet.shared.json.InstantSerializer
import com.google.gson.Gson
import com.google.gson.GsonBuilder

object BNGson {
    var ownSerializer: Gson = GsonBuilder().create()
    fun create(): Gson {
        return base.setPrettyPrinting().create()
    }

    fun createNotPrettyPrinting(): Gson {
        return base.create()
    }

    private val base: GsonBuilder
        get() = GsonBuilder()
            .registerTypeAdapter(Color::class.java, ColorSerializer())
            .registerTypeAdapter(Duration::class.java, DurationSerializer())
            .registerTypeAdapter(Instant::class.java, InstantSerializer())
}
