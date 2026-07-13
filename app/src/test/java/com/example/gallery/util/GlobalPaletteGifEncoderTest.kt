package com.example.gallery.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode

class GlobalPaletteGifEncoderTest {
    @Test
    fun encodesLargeIndexedFrameWithoutChangingColors() {
        val width = 128
        val height = 96
        val palette = testPalette()
        val indices = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            (x * 37 + y * 73 + x * y) and 0xFF
        }
        val output = ByteArrayOutputStream()
        GlobalPaletteGifEncoder(output, width, height, palette).apply {
            addFrame(indices, width, height, delayMs = 80L)
            finish()
        }

        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        ImageIO.createImageInputStream(ByteArrayInputStream(output.toByteArray())).use { input ->
            reader.input = input
            assertEquals(1, reader.getNumImages(true))
            val decoded = reader.read(0)
            assertEquals(width, decoded.width)
            assertEquals(height, decoded.height)
            for (index in indices.indices) {
                val x = index % width
                val y = index / width
                assertEquals(palette[indices[index]] and 0x00FFFFFF, decoded.getRGB(x, y) and 0x00FFFFFF)
            }
        }
        reader.dispose()
    }

    @Test
    fun writesGlobalPaletteAndTransparentDoNotDisposeFrame() {
        val palette = testPalette()
        val output = ByteArrayOutputStream()
        GlobalPaletteGifEncoder(output, 4, 4, palette).apply {
            addFrame(IntArray(16) { 1 }, 4, 4, delayMs = 80L)
            addFrame(
                colorIndices = intArrayOf(255, 2, 255, 255),
                width = 2,
                height = 2,
                left = 1,
                top = 1,
                delayMs = 90L,
                transparentColorIndex = 255
            )
            finish()
        }

        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        ImageIO.createImageInputStream(ByteArrayInputStream(output.toByteArray())).use { input ->
            reader.input = input
            assertEquals(2, reader.getNumImages(true))

            val streamRoot = reader.streamMetadata
                .getAsTree("javax_imageio_gif_stream_1.0") as IIOMetadataNode
            assertNotNull(streamRoot.child("GlobalColorTable"))

            val firstRoot = reader.getImageMetadata(0)
                .getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
            val secondRoot = reader.getImageMetadata(1)
                .getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
            assertNull(firstRoot.child("LocalColorTable"))
            assertNull(secondRoot.child("LocalColorTable"))

            val firstControl = firstRoot.child("GraphicControlExtension")
            val secondControl = secondRoot.child("GraphicControlExtension")
            assertEquals("doNotDispose", firstControl?.getAttribute("disposalMethod"))
            assertEquals("FALSE", firstControl?.getAttribute("transparentColorFlag"))
            assertEquals("doNotDispose", secondControl?.getAttribute("disposalMethod"))
            assertEquals("TRUE", secondControl?.getAttribute("transparentColorFlag"))
            assertEquals("255", secondControl?.getAttribute("transparentColorIndex"))
        }
        reader.dispose()
    }

    private fun IIOMetadataNode.child(name: String): IIOMetadataNode? {
        for (index in 0 until childNodes.length) {
            val child = childNodes.item(index)
            if (child.nodeName == name) return child as IIOMetadataNode
        }
        return null
    }

    private fun testPalette(): IntArray = IntArray(256) { index ->
        val red = (index ushr 5) * 255 / 7
        val green = (index ushr 2 and 7) * 255 / 7
        val blue = (index and 3) * 255 / 3
        (red shl 16) or (green shl 8) or blue
    }
}
