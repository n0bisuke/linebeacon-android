package com.example.linebeacon.beacon

/**
 * Small helpers to convert hex strings to/from byte arrays.
 *
 * Accepted inputs:
 *  - upper case / lower case hex digits (e.g. "32AF51" / "32af51")
 *  - optional whitespace anywhere (e.g. "32 AF 51 9E 88")
 * Anything else is rejected.
 */
object Hex {

    /** Returns the hex representation using upper case letters. */
    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(TABLE[v ushr 4])
            sb.append(TABLE[v and 0x0F])
        }
        return sb.toString()
    }

    /**
     * Decodes [input] to bytes, or returns null when it is not valid hex.
     * An empty (or whitespace only) input decodes to an empty array.
     */
    fun decode(input: String): ByteArray? {
        val cleaned = input.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return EMPTY
        if (cleaned.length % 2 != 0) return null
        val out = ByteArray(cleaned.length / 2)
        for (i in out.indices) {
            val hi = nibble(cleaned[i * 2]) ?: return null
            val lo = nibble(cleaned[i * 2 + 1]) ?: return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    fun isValid(input: String): Boolean = decode(input) != null

    private fun nibble(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> null
    }

    private val TABLE = "0123456789ABCDEF".toCharArray()
    private val EMPTY = ByteArray(0)

    /** Formats an Int as two hex digits (big endian byte order). */
    fun byte(int: Int): String = "%02X".format(int and 0xFF)
}
