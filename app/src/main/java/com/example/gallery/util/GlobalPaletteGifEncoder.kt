package com.example.gallery.util

import java.io.OutputStream
import java.util.Arrays
import kotlin.math.max

/** Writes GIF89a animations with one global palette and optional transparent delta frames. */
class GlobalPaletteGifEncoder(
    private val output: OutputStream,
    private val canvasWidth: Int,
    private val canvasHeight: Int,
    palette: IntArray,
    loopCount: Int = 0
) {
    private val colorTableSize = palette.size.coerceAtLeast(2).nextPowerOfTwo()
    private var finished = false

    init {
        require(canvasWidth in 1..0xFFFF && canvasHeight in 1..0xFFFF)
        require(palette.size in 1..256)
        require(colorTableSize <= 256)
        writeAscii("GIF89a")
        writeShort(canvasWidth)
        writeShort(canvasHeight)
        val colorTableSizeField = Integer.numberOfTrailingZeros(colorTableSize) - 1
        output.write(0x80 or 0x70 or colorTableSizeField)
        output.write(0)
        output.write(0)
        for (index in 0 until colorTableSize) {
            val color = palette.getOrElse(index) { 0 }
            output.write(color ushr 16 and 0xFF)
            output.write(color ushr 8 and 0xFF)
            output.write(color and 0xFF)
        }
        writeLoopExtension(loopCount)
    }

    fun addFrame(
        colorIndices: IntArray,
        width: Int,
        height: Int,
        left: Int = 0,
        top: Int = 0,
        delayMs: Long,
        transparentColorIndex: Int? = null
    ) {
        check(!finished) { "GIF encoding has already finished" }
        require(width > 0 && height > 0 && colorIndices.size == width * height)
        require(left >= 0 && top >= 0 && left + width <= canvasWidth && top + height <= canvasHeight)
        transparentColorIndex?.let { require(it in 0 until colorTableSize) }

        writeGraphicsControlExtension(delayMs, transparentColorIndex)
        output.write(0x2C)
        writeShort(left)
        writeShort(top)
        writeShort(width)
        writeShort(height)
        output.write(0)

        val minimumCodeSize = max(2, Integer.numberOfTrailingZeros(colorTableSize))
        output.write(minimumCodeSize)
        FastGifLzwEncoder.encode(
            indices = colorIndices,
            minimumCodeSize = minimumCodeSize,
            output = output
        )
    }

    fun finish() {
        if (finished) return
        output.write(0x3B)
        finished = true
    }

    private fun writeGraphicsControlExtension(delayMs: Long, transparentColorIndex: Int?) {
        output.write(0x21)
        output.write(0xF9)
        output.write(4)
        val disposalDoNotDispose = 1 shl 2
        output.write(disposalDoNotDispose or if (transparentColorIndex != null) 1 else 0)
        val delayCentiseconds = ((delayMs + 5L) / 10L).coerceIn(1L, 0xFFFFL).toInt()
        writeShort(delayCentiseconds)
        output.write(transparentColorIndex ?: 0)
        output.write(0)
    }

    private fun writeLoopExtension(loopCount: Int) {
        output.write(0x21)
        output.write(0xFF)
        output.write(11)
        writeAscii("NETSCAPE2.0")
        output.write(3)
        output.write(1)
        writeShort(loopCount.coerceIn(0, 0xFFFF))
        output.write(0)
    }

    private fun writeShort(value: Int) {
        output.write(value and 0xFF)
        output.write(value ushr 8 and 0xFF)
    }

    private fun writeAscii(value: String) {
        value.forEach { output.write(it.code) }
    }
}

private object FastGifLzwEncoder {
    private const val MAX_CODE = 4095
    private const val HASH_SIZE = 8192
    private const val HASH_MASK = HASH_SIZE - 1

    fun encode(indices: IntArray, minimumCodeSize: Int, output: OutputStream) {
        require(indices.isNotEmpty())
        val clearCode = 1 shl minimumCodeSize
        val endCode = clearCode + 1
        var nextCode = endCode + 1
        var codeSize = minimumCodeSize + 1
        val dictionaryKeys = IntArray(HASH_SIZE)
        val dictionaryValues = IntArray(HASH_SIZE)
        Arrays.fill(dictionaryKeys, -1)
        val writer = GifSubBlockBitWriter(output)

        writer.write(clearCode, codeSize)
        var prefix = indices[0]
        for (position in 1 until indices.size) {
            val suffix = indices[position]
            val key = prefix shl 8 or suffix
            var slot = (key * 31) and HASH_MASK
            while (true) {
                val storedKey = dictionaryKeys[slot]
                if (storedKey == key) {
                    prefix = dictionaryValues[slot]
                    break
                }
                if (storedKey == -1) {
                    writer.write(prefix, codeSize)
                    if (nextCode <= MAX_CODE) {
                        dictionaryKeys[slot] = key
                        dictionaryValues[slot] = nextCode++
                        if (nextCode > 1 shl codeSize && codeSize < 12) codeSize++
                    } else {
                        writer.write(clearCode, codeSize)
                        Arrays.fill(dictionaryKeys, -1)
                        nextCode = endCode + 1
                        codeSize = minimumCodeSize + 1
                    }
                    prefix = suffix
                    break
                }
                slot = (slot + 1) and HASH_MASK
            }
        }
        writer.write(prefix, codeSize)
        writer.write(endCode, codeSize)
        writer.finish()
    }
}

private class GifSubBlockBitWriter(private val output: OutputStream) {
    private val block = ByteArray(255)
    private var blockSize = 0
    private var bitBuffer = 0L
    private var bitCount = 0

    fun write(code: Int, codeSize: Int) {
        bitBuffer = bitBuffer or (code.toLong() shl bitCount)
        bitCount += codeSize
        while (bitCount >= 8) {
            appendByte((bitBuffer and 0xFF).toInt())
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    fun finish() {
        if (bitCount > 0) appendByte((bitBuffer and 0xFF).toInt())
        flushBlock()
        output.write(0)
    }

    private fun appendByte(value: Int) {
        block[blockSize++] = value.toByte()
        if (blockSize == block.size) flushBlock()
    }

    private fun flushBlock() {
        if (blockSize == 0) return
        output.write(blockSize)
        output.write(block, 0, blockSize)
        blockSize = 0
    }
}

private fun Int.nextPowerOfTwo(): Int {
    var value = 1
    while (value < this) value = value shl 1
    return value
}
