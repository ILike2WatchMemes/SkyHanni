import at.hannibal2.skyhanni.utils.compat.createResourceLocation
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.util.ResourceLocation
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode

object GifLoader {
    data class GifFrame(val textureLocation: ResourceLocation, val delay: Int)

    private val cache = mutableMapOf<ResourceLocation, List<GifFrame>>()

    fun loadGifFrames(gifLocation: ResourceLocation): List<GifFrame> {
        cache[gifLocation]?.let { return it }

        val resource = Minecraft.getMinecraft().resourceManager.getResource(gifLocation)
        val stream = resource.inputStream.buffered()
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        val input = ImageIO.createImageInputStream(stream)
        reader.input = input

        val width = reader.getWidth(0)
        val height = reader.getHeight(0)
        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val gCanvas = canvas.graphics as Graphics2D

        val frameAmount = reader.getNumImages(true)
        val frames = ArrayList<GifFrame>(frameAmount)

        for (i in 0 until frameAmount) {
            try {
                val frame = reader.read(i)
                val metadata = reader.getImageMetadata(i)
                val delay = extractDelay(metadata) ?: 100
                val (x, y) = extractImagePosition(metadata)
                val disposal = extractDisposalMethod(metadata)

                gCanvas.drawImage(frame, x, y, null)

                // Copy canvas to texture
                val copy = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                copy.graphics.drawImage(canvas, 0, 0, null)

                val frameId = createResourceLocation("skyhanni", "frame_$i")
                Minecraft.getMinecraft().textureManager.loadTexture(frameId, DynamicTexture(copy))
                frames.add(GifFrame(frameId, delay))

                // Handle disposal
                if (disposal == "restoreToBackgroundColor") {
                    gCanvas.composite = AlphaComposite.Src
                    gCanvas.color = Color(0, 0, 0, 0)
                    gCanvas.fillRect(x, y, frame.width, frame.height)
                    gCanvas.composite = AlphaComposite.SrcOver
                }
            } catch (e: Exception) {
                continue
            }
        }

        gCanvas.dispose()
        input.close()
        stream.close()

        cache[gifLocation] = frames
        return frames
    }

    private fun extractDelay(meta: IIOMetadata): Int? {
        val gce = getNode(meta, "GraphicControlExtension")
        return gce?.getAttribute("delayTime")?.toIntOrNull()?.times(10)
    }

    private fun extractDisposalMethod(meta: IIOMetadata): String {
        return getNode(meta, "GraphicControlExtension")?.getAttribute("disposalMethod") ?: "none"
    }

    private fun extractImagePosition(meta: IIOMetadata): Pair<Int, Int> {
        val node = getNode(meta, "ImageDescriptor")
        val x = node?.getAttribute("imageLeftPosition")?.toIntOrNull() ?: 0
        val y = node?.getAttribute("imageTopPosition")?.toIntOrNull() ?: 0
        return x to y
    }

    private fun getNode(meta: IIOMetadata, name: String): IIOMetadataNode? {
        val root = meta.getAsTree("javax_imageio_gif_image_1.0") as? IIOMetadataNode ?: return null
        for (i in 0 until root.length) {
            val node = root.item(i)
            if (node is IIOMetadataNode && node.nodeName == name) return node
        }
        return null
    }
}
