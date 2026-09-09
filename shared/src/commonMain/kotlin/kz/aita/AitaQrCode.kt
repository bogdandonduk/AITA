package kz.aita

import kotlin.math.abs
import kotlin.math.max

/** Immutable, local-only QR modules. Does not retain the input string or transmit any data. */
class AitaQrMatrix internal constructor(val size: Int, private val cells: BooleanArray, val mask: Int) {
    operator fun get(x: Int, y: Int): Boolean = cells[y * size + x]
}

/**
 * QR Model 2, UTF-8 byte mode, medium error correction, versions 1–40.
 * No network, image service, platform dependency, or persistent cache is involved.
 * Algorithm reference: https://www.nayuki.io/page/qr-code-generator-library
 * Uses integer modules; the renderer supplies the mandatory four-module quiet zone.
 */
object AitaQrCode {
    fun encode(text: String): AitaQrMatrix {
        val bytes = text.encodeToByteArray()
        require(bytes.size <= 2331) { "QR content exceeds the supported capacity" }
        val version = (1..40).firstOrNull { v ->
            val countBits = if (v < 10) 8 else 16
            bytes.size < (1 shl countBits) && 4 + countBits + bytes.size * 8 <= dataCapacity(v) * 8
        } ?: throw IllegalArgumentException("QR content exceeds the supported capacity")
        val capacity = dataCapacity(version)
        val bits = ArrayList<Boolean>(capacity * 8)
        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) bits.add(((value ushr i) and 1) != 0)
        }
        append(4, 4)
        append(bytes.size, if (version < 10) 8 else 16)
        bytes.forEach { append(it.toInt() and 255, 8) }
        append(0, minOf(4, capacity * 8 - bits.size))
        while (bits.size % 8 != 0) bits.add(false)
        val data = IntArray(capacity)
        for (i in bits.indices) if (bits[i]) data[i / 8] = data[i / 8] or (1 shl (7 - i % 8))
        for (i in bits.size / 8 until capacity) data[i] = if ((i - bits.size / 8) % 2 == 0) 0xEC else 0x11
        val codewords = interleave(version, data)
        val size = version * 4 + 17
        val cells = BooleanArray(size * size)
        val function = BooleanArray(cells.size)
        fun mark(x: Int, y: Int, dark: Boolean) {
            if (x in 0 until size && y in 0 until size) { cells[y * size + x] = dark; function[y * size + x] = true }
        }
        for (i in 0 until size) { mark(6, i, i % 2 == 0); mark(i, 6, i % 2 == 0) }
        for ((cx, cy) in listOf(3 to 3, size - 4 to 3, 3 to size - 4)) {
            for (dy in -4..4) for (dx in -4..4) {
                val d = max(abs(dx), abs(dy)); mark(cx + dx, cy + dy, d != 2 && d != 4)
            }
        }
        if (version > 1) {
            val count = version / 7 + 2
            val step = if (version == 32) 26 else (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2
            val positions = IntArray(count)
            positions[0] = 6
            for (i in 1 until count) positions[i] = size - 7 - (count - 1 - i) * step
            for (i in positions.indices) for (j in positions.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == count - 1) || (i == count - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) mark(positions[i] + dx, positions[j] + dy, max(abs(dx), abs(dy)) != 1)
            }
        }
        fun format(mask: Int) {
            var remainder = mask // Medium ECC's format bits are 00.
            repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
            val value = ((mask shl 10) or remainder) xor 0x5412
            fun bit(i: Int) = ((value ushr i) and 1) != 0
            for (i in 0..5) mark(8, i, bit(i))
            mark(8, 7, bit(6)); mark(8, 8, bit(7)); mark(7, 8, bit(8))
            for (i in 9..14) mark(14 - i, 8, bit(i))
            for (i in 0..7) mark(size - 1 - i, 8, bit(i))
            for (i in 8..14) mark(8, size - 15 + i, bit(i))
            mark(8, size - 8, true)
        }
        format(0)
        if (version >= 7) {
            var remainder = version
            repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
            val value = (version shl 12) or remainder
            for (i in 0 until 18) {
                val a = size - 11 + i % 3; val b = i / 3; val dark = ((value ushr i) and 1) != 0
                mark(a, b, dark); mark(b, a, dark)
            }
        }
        var bitIndex = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vertical in 0 until size) {
                val y = if (((right + 1) and 2) == 0) size - 1 - vertical else vertical
                for (j in 0..1) {
                    val x = right - j
                    if (!function[y * size + x]) {
                        if (bitIndex < codewords.size * 8) cells[y * size + x] = ((codewords[bitIndex / 8] ushr (7 - bitIndex % 8)) and 1) != 0
                        bitIndex++
                    }
                }
            }
            right -= 2
        }
        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                val at = y * size + x
                if (!function[at] && invert) cells[at] = !cells[at]
            }
        }
        var bestMask = 0
        var bestScore = Int.MAX_VALUE
        for (mask in 0..7) {
            applyMask(mask); format(mask)
            val score = penalty(cells, size)
            if (score < bestScore) { bestScore = score; bestMask = mask }
            applyMask(mask)
        }
        applyMask(bestMask); format(bestMask)
        return AitaQrMatrix(size, cells, bestMask)
    }

    private fun rawCodewords(version: Int): Int {
        var modules = (16 * version + 128) * version + 64
        if (version >= 2) {
            val align = version / 7 + 2
            modules -= (25 * align - 10) * align - 55
            if (version >= 7) modules -= 36
        }
        return modules / 8
    }
    private fun dataCapacity(version: Int) = rawCodewords(version) - ecc[version] * blocks[version]
    private fun multiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) { z = (z shl 1) xor ((z ushr 7) * 0x11D); z = z xor (((y ushr i) and 1) * x) }
        return z
    }
    private fun interleave(version: Int, data: IntArray): IntArray {
        val count = blocks[version]; val degree = ecc[version]; val raw = rawCodewords(version)
        val shortCount = count - raw % count; val shortLength = raw / count - degree
        val divisor = IntArray(degree); divisor[degree - 1] = 1
        var root = 1
        repeat(degree) {
            for (j in divisor.indices) {
                divisor[j] = multiply(divisor[j], root)
                if (j + 1 < degree) divisor[j] = divisor[j] xor divisor[j + 1]
            }
            root = multiply(root, 2)
        }
        var offset = 0
        val parts = Array(count) { block ->
            val length = shortLength + if (block < shortCount) 0 else 1
            data.copyOfRange(offset, offset + length).also { offset += length }
        }
        val checks = parts.map { part ->
            val remainder = IntArray(degree)
            part.forEach { value ->
                val factor = value xor remainder[0]
                for (i in 0 until degree - 1) remainder[i] = remainder[i + 1]
                remainder[degree - 1] = 0
                for (i in remainder.indices) remainder[i] = remainder[i] xor multiply(divisor[i], factor)
            }
            remainder
        }
        val output = ArrayList<Int>(raw)
        for (i in 0..shortLength) parts.forEach { if (i < it.size) output.add(it[i]) }
        for (i in 0 until degree) checks.forEach { output.add(it[i]) }
        check(output.size == raw)
        return output.toIntArray()
    }
    private fun penalty(cells: BooleanArray, size: Int): Int {
        var score = 0
        for (vertical in listOf(false, true)) for (line in 0 until size) {
            var previous = false; var run = 0; var pattern = 0
            for (i in 0 until size) {
                val dark = if (vertical) cells[i * size + line] else cells[line * size + i]
                if (i > 0 && dark == previous) run++ else { if (run >= 5) score += run - 2; previous = dark; run = 1 }
                pattern = ((pattern shl 1) and 0x7FF) or if (dark) 1 else 0
                if (i >= 10 && (pattern == 0x5D0 || pattern == 0x05D)) score += 40
            }
            if (run >= 5) score += run - 2
        }
        for (y in 0 until size - 1) for (x in 0 until size - 1) {
            val c = cells[y * size + x]
            if (c == cells[y * size + x + 1] && c == cells[(y + 1) * size + x] && c == cells[(y + 1) * size + x + 1]) score += 3
        }
        score += abs(cells.count { it } * 20 - cells.size * 10) / cells.size * 10
        return score
    }
    // ISO QR medium error-correction block layout, indexed by symbol version (0 unused).
    private val ecc = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28)
    private val blocks = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49)
}
