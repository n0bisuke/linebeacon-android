package com.example.linebeacon.beacon

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexTest {

    @Test
    fun `decode upper and lower case produce the same bytes`() {
        val upper = Hex.decode("32AF519E88")
        val lower = Hex.decode("32af519e88")
        val mixed = Hex.decode("32aF519E88")
        val expected = byteArrayOf(
            0x32.toByte(), 0xAF.toByte(), 0x51.toByte(), 0x9E.toByte(), 0x88.toByte()
        )
        assertArrayEquals(expected, upper)
        assertArrayEquals(expected, lower)
        assertArrayEquals(expected, mixed)
    }

    @Test
    fun `decode ignores whitespace`() {
        val expected = byteArrayOf(
            0x32.toByte(), 0xAF.toByte(), 0x51.toByte(), 0x9E.toByte(), 0x88.toByte()
        )
        assertArrayEquals(expected, Hex.decode("32 AF 51 9E 88"))
        assertArrayEquals(expected, Hex.decode(" 32AF 519E88 "))
    }

    @Test
    fun `encode produces uppercase hex`() {
        assertEquals("32AF519E88", Hex.encode(Hex.decode("32af519e88")!!))
        assertEquals("027F00", Hex.encode(byteArrayOf(0x02, 0x7F.toByte(), 0x00)))
    }

    @Test
    fun `encode decode round trip`() {
        val bytes = byteArrayOf(0x00, 0x01, 0x7F.toByte(), (0x80).toByte(), 0xFF.toByte())
        assertArrayEquals(bytes, Hex.decode(Hex.encode(bytes))!!)
    }

    @Test
    fun `reject odd length`() {
        assertNull(Hex.decode("321"))
        assertFalse(Hex.isValid("321"))
    }

    @Test
    fun `reject non hex characters`() {
        assertNull(Hex.decode("zz"))
        assertNull(Hex.decode("32G1"))
        assertNull(Hex.decode("32 AF-51"))
        assertFalse(Hex.isValid("0x32AF"))
    }

    @Test
    fun `empty input decodes to empty array`() {
        assertEquals(0, Hex.decode("")!!.size)
        assertEquals(0, Hex.decode("   ")!!.size)
        assertTrue(Hex.isValid(""))
    }
}
