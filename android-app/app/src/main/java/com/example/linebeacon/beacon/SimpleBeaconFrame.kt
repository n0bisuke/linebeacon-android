package com.example.linebeacon.beacon

import java.util.UUID

/**
 * LINE Simple Beacon frame builder.
 *
 * Spec (official): https://github.com/line/line-simple-beacon
 *  https://developers.line.biz/ja/docs/messaging-api/beacon-device-spec/
 *
 * A BLE advertising packet contains (among others) this AD structure:
 *
 *   [len] [0x16] [0x6F 0xFE] [frame ...]
 *          ^Service Data   ^16-bit UUID 0xFE6F (little endian)
 *
 * The "frame" (LINE Simple Beacon Frame) is 8..20 bytes long:
 *
 *   +-----------+-----------+------------------+--------------+
 *   | FrameType | HWID      | Measured TxPower | Device Msg   |
 *   | 1 byte    | 5 bytes   | 1 byte           | 1..13 bytes  |
 *   | 0x02      | (issued)  | 0x7F             | user-defined |
 *   +-----------+-----------+------------------+--------------+
 *
 * All fields are required and multi-byte values are big endian.
 * The Device Message must be 1..13 bytes; when it is not used, a single
 * 0x00 byte is sent instead (see the spec).
 */
object SimpleBeaconFrame {

    /** 16-bit UUID assigned to LINE (LY Corporation). */
    const val SERVICE_UUID_16BIT: Int = 0xFE6F

    /** Same UUID expressed as a full 128-bit Bluetooth base UUID. */
    val SERVICE_UUID: UUID = UUID.fromString("0000fe6f-0000-1000-8000-00805f9b34fb")

    /** Only 0x02 (LINE Beacon) can be used as Frame Type. */
    const val FRAME_TYPE: Int = 0x02

    /** Only 0x7F can be used as Measured TxPower for Simple Beacon. */
    const val MEASURED_TX_POWER: Int = 0x7F

    const val HWID_BYTES: Int = 5
    const val DEVICE_MESSAGE_MAX_BYTES: Int = 13

    /** 1(FrameType) + 5(HWID) + 1(TxPower) + 1..13(DeviceMessage) */
    const val FRAME_MIN_BYTES: Int = 8
    const val FRAME_MAX_BYTES: Int = 20

    /** HWID must be a 10 digit hex string (= 5 bytes). */
    const val HWID_HEX_LENGTH: Int = HWID_BYTES * 2

    /** Device Message accepts up to 26 hex digits (= 13 bytes). */
    const val DEVICE_MESSAGE_MAX_HEX_LENGTH: Int = DEVICE_MESSAGE_MAX_BYTES * 2

    /**
     * Builds a LINE Simple Beacon Frame byte array.
     *
     * @param hwid 5 bytes HWID issued by the LINE Official Account Manager.
     * @param deviceMessage 1..13 bytes device message.
     */
    fun buildFrame(hwid: ByteArray, deviceMessage: ByteArray): ByteArray {
        require(hwid.size == HWID_BYTES) {
            "HWID must be $HWID_BYTES bytes but was ${hwid.size}"
        }
        require(deviceMessage.size in 1..DEVICE_MESSAGE_MAX_BYTES) {
            "Device Message must be 1..$DEVICE_MESSAGE_MAX_BYTES bytes but was ${deviceMessage.size}"
        }
        val frame = ByteArray(1 + HWID_BYTES + 1 + deviceMessage.size)
        frame[0] = FRAME_TYPE.toByte()
        hwid.copyInto(frame, 1)
        frame[1 + HWID_BYTES] = MEASURED_TX_POWER.toByte()
        deviceMessage.copyInto(frame, 2 + HWID_BYTES)
        return frame
    }

    /**
     * Parses the Device Message input. When left empty, a single 0x00 byte
     * is returned, as the spec says: "Set value as 0x00 if the Device Message
     * property is not used."
     */
    fun deviceMessageBytes(deviceMessageHex: String): ByteArray? {
        val parsed = Hex.decode(deviceMessageHex) ?: return null
        return if (parsed.isEmpty()) byteArrayOf(0x00) else parsed
    }

    /**
     * Validates a HWID input and returns an error message, or null when valid.
     */
    fun hwidError(hwidHex: String): String? {
        val cleaned = hwidHex.filterNot { it.isWhitespace() }
        return when {
            cleaned.isEmpty() -> "HWIDを入力してください"
            !Hex.isValid(cleaned) -> "HWIDはHEX文字列(0-9, A-F)で入力してください"
            cleaned.length != HWID_HEX_LENGTH ->
                "HWIDは${HWID_BYTES}バイト(${HWID_HEX_LENGTH}桁)のHEXで入力してください"
            else -> null
        }
    }

    /**
     * Validates a Device Message input and returns an error message, or null
     * when valid (blank input is allowed - it becomes a single 0x00 byte).
     */
    fun deviceMessageError(deviceMessageHex: String): String? = when {
        !Hex.isValid(deviceMessageHex) -> "Device MessageはHEX文字列(0-9, A-F)で入力してください"
        Hex.decode(deviceMessageHex)!!.size > DEVICE_MESSAGE_MAX_BYTES ->
            "Device Messageは最大${DEVICE_MESSAGE_MAX_BYTES}バイト(${DEVICE_MESSAGE_MAX_HEX_LENGTH}桁)までです"
        else -> null
    }
}
