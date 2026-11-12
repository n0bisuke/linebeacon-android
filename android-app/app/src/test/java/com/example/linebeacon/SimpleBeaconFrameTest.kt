package com.example.linebeacon.beacon

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleBeaconFrameTest {

    /**
     * Spec example (https://github.com/line/line-simple-beacon README.en.md):
     * HWID = 32AF519E88, Device Message = 0032006900FAFFFF000199FF88 (13 bytes)
     *
     *   0x02 | 32 AF 51 9E 88 | 0x7F | 00 32 00 69 00 FA FF FF 00 01 99 FF 88
     */
    @Test
    fun `builds the spec example frame`() {
        val hwid = Hex.decode("32AF519E88")!!
        val dm = Hex.decode("00 32 00 69 00 FA FF FF 00 01 99 FF 88")!!
        assertEquals(13, dm.size)

        val frame = SimpleBeaconFrame.buildFrame(hwid, dm)

        assertEquals(SimpleBeaconFrame.FRAME_MAX_BYTES, frame.size)
        assertArrayEquals(
            byteArrayOf(
                0x02, // Frame Type = LINE Beacon
                0x32.toByte(), 0xAF.toByte(), 0x51.toByte(), 0x9E.toByte(), 0x88.toByte(), // HWID
                0x7F.toByte(), // Measured TxPower
                0x00, 0x32.toByte(), 0x00, 0x69.toByte(), 0x00, // Device Message...
                0xFA.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0x01, 0x99.toByte(), 0xFF.toByte(), 0x88.toByte(),
            ),
            frame,
        )
    }

    @Test
    fun `empty device message becomes a single 0x00 byte`() {
        val hwid = Hex.decode("32AF519E88")!!
        val dm = SimpleBeaconFrame.deviceMessageBytes("")!!
        assertArrayEquals(byteArrayOf(0x00), dm)

        val frame = SimpleBeaconFrame.buildFrame(hwid, dm)
        assertEquals(SimpleBeaconFrame.FRAME_MIN_BYTES, frame.size)
        assertEquals("0232AF519E887F00", Hex.encode(frame))
    }

    @Test
    fun `frame with one byte device message is 8 bytes`() {
        val frame = SimpleBeaconFrame.buildFrame(Hex.decode("0000000000")!!, byteArrayOf(0x01))
        assertEquals(8, frame.size)
        assertEquals("0200000000007F01", Hex.encode(frame))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects hwid with wrong size`() {
        SimpleBeaconFrame.buildFrame(Hex.decode("32AF519E")!!, byteArrayOf(0x00))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects device message longer than 13 bytes`() {
        SimpleBeaconFrame.buildFrame(
            Hex.decode("32AF519E88")!!,
            Hex.decode("000000000000000000000000000000")!!, // 15 bytes
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty device message array`() {
        SimpleBeaconFrame.buildFrame(Hex.decode("32AF519E88")!!, ByteArray(0))
    }

    @Test
    fun `constants match the official spec`() {
        assertEquals(0xFE6F, SimpleBeaconFrame.SERVICE_UUID_16BIT)
        assertEquals("0000fe6f-0000-1000-8000-00805f9b34fb", SimpleBeaconFrame.SERVICE_UUID.toString())
        assertEquals(0x02, SimpleBeaconFrame.FRAME_TYPE)
        assertEquals(0x7F, SimpleBeaconFrame.MEASURED_TX_POWER)
        assertEquals(5, SimpleBeaconFrame.HWID_BYTES)
        assertEquals(13, SimpleBeaconFrame.DEVICE_MESSAGE_MAX_BYTES)
        assertEquals(8, SimpleBeaconFrame.FRAME_MIN_BYTES)
        assertEquals(20, SimpleBeaconFrame.FRAME_MAX_BYTES)
    }

    // ---- 入力検証 ----

    @Test
    fun `hwid validation`() {
        assertNull(SimpleBeaconFrame.hwidError("32AF519E88"))
        assertNull(SimpleBeaconFrame.hwidError("32af519e88"))
        assertNotNull(SimpleBeaconFrame.hwidError("")) // 空
        assertNotNull(SimpleBeaconFrame.hwidError("32AF519E8")) // 9桁
        assertNotNull(SimpleBeaconFrame.hwidError("32AF519E8800")) // 12桁
        assertNotNull(SimpleBeaconFrame.hwidError("32AF519E8Z")) // 非HEX
    }

    @Test
    fun `device message validation`() {
        assertNull(SimpleBeaconFrame.deviceMessageError("")) // 空欄は許可
        assertNull(SimpleBeaconFrame.deviceMessageError("0011998877665544332211"))
        assertNull(SimpleBeaconFrame.deviceMessageError("00112233445566778899AABBCC")) // 13バイト
        assertNotNull(SimpleBeaconFrame.deviceMessageError("00112233445566778899AABBCCDD")) // 14バイト
        assertNotNull(SimpleBeaconFrame.deviceMessageError("GG")) // 非HEX
    }

    @Test
    fun `config factory accepts valid input and stores normalized values`() {
        val config = BeaconConfig.newInstance("32af519e88", "00 11").getOrThrow()
        assertEquals("32AF519E88", config.hwidHex)
        assertEquals("0011", config.deviceMessageHex)
        assertEquals("0232AF519E887F0011", config.frameHex)
    }

    @Test
    fun `text to device message hex conversion`() {
        // 日本語1文字=3バイトUTF-8。「おはよう」= 12バイトで収まる
        assertEquals("E3818AE381AFE38288E38186", SimpleBeaconFrame.deviceMessageHexFromText("おはよう"))
        assertEquals("", SimpleBeaconFrame.deviceMessageHexFromText("")) // 空欄可(0x00になる)
        // 5文字=15バイトは上限超過でnull。ASCIIは1バイト/文字
        assertNull(SimpleBeaconFrame.deviceMessageHexFromText("こんにちは"))
        assertNull(SimpleBeaconFrame.deviceMessageHexFromText("12345678901234")) // 14バイト
        assertEquals("414243", SimpleBeaconFrame.deviceMessageHexFromText("ABC"))
    }

    @Test
    fun `device message hex to text conversion`() {
        assertEquals("おはよう", SimpleBeaconFrame.textFromDeviceMessageHex("E3818AE381AFE38288E38186"))
        assertNull(SimpleBeaconFrame.textFromDeviceMessageHex("")) // 空
        assertNull(SimpleBeaconFrame.textFromDeviceMessageHex("DEADBF")) // 完全なUTF-8ではない
        assertNull(SimpleBeaconFrame.textFromDeviceMessageHex("0232af519e887f")) // 制御文字を含む
        assertNull(SimpleBeaconFrame.textFromDeviceMessageHex("GG")) // 非HEX
    }

    @Test
    fun `battery dm message`() {
        // "🔋85%" = U+1F50B(4B) + "85"(2B) + "%"(1B) = 7バイトUTF-8
        assertEquals("🔋85%", SimpleBeaconFrame.batteryDeviceMessageText(85))
        assertEquals("F09F948B3835 25".replace(" ", ""), SimpleBeaconFrame.batteryDeviceMessageHex(85))
        // 桁数は可変(1%..100%)
        assertEquals("F09F948B3125", SimpleBeaconFrame.batteryDeviceMessageHex(1)) // "🔋1%"
        assertEquals("F09F948B31303025", SimpleBeaconFrame.batteryDeviceMessageHex(100)) // "🔋100%"
        // クランプ
        assertEquals(SimpleBeaconFrame.batteryDeviceMessageHex(0), SimpleBeaconFrame.batteryDeviceMessageHex(-5))
        assertEquals(SimpleBeaconFrame.batteryDeviceMessageHex(100), SimpleBeaconFrame.batteryDeviceMessageHex(120))
        // 13バイト上限を大きく下回る
        assertTrue(Hex.decode(SimpleBeaconFrame.batteryDeviceMessageHex(100))!!.size <= 13)

        // フレームとして組める
        val frame = SimpleBeaconFrame.buildFrame(
            Hex.decode("32AF519E88")!!,
            SimpleBeaconFrame.deviceMessageBytes(SimpleBeaconFrame.batteryDeviceMessageHex(85))!!,
        )
        assertEquals("0232AF519E887FF09F948B383525", Hex.encode(frame))
    }

    @Test
    fun `config factory rejects invalid input`() {
        assertTrue(BeaconConfig.newInstance("12345", "").isFailure)
        assertTrue(BeaconConfig.newInstance("32AF519E88", "XYZ").isFailure)
    }

    // ---- HWIDマスク表示 ----

    @Test
    fun `hwid masking keeps first and last two digits`() {
        assertEquals("32••••••88", SimpleBeaconFrame.maskHwid("32AF519E88"))
        assertEquals("01••••••05", SimpleBeaconFrame.maskHwid("0102030405"))
    }

    @Test
    fun `hwid masking leaves unexpected length untouched`() {
        // 10桁以外の入力(=未検証・空欄等)はそのまま表示する
        assertEquals("", SimpleBeaconFrame.maskHwid(""))
        assertEquals("32AF519E8", SimpleBeaconFrame.maskHwid("32AF519E8"))
    }

    @Test
    fun `frame masking blurs only hwid part`() {
        // HWID部分だけが maskHwid と同じ形式になる(frame typeとdm部分は元のまま)
        val frame = "0232AF519E887FF09F948B383525"
        assertEquals("0232••••••887FF09F948B383525", SimpleBeaconFrame.maskHwidInFrameHex(frame))
        // 短い文字列はそのまま
        assertEquals("0232", SimpleBeaconFrame.maskHwidInFrameHex("0232"))
        assertEquals("", SimpleBeaconFrame.maskHwidInFrameHex(""))
    }
}
