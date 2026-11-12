package com.example.linebeacon.beacon

/**
 * Validated beacon parameters. Building one of these from user input always
 * runs through [newInstance], so any [BeaconConfig] that exists is known to be
 * within the LINE Simple Beacon limits.
 */
data class BeaconConfig(
    val hwidHex: String,
    val deviceMessageHex: String,
) {

    val hwid: ByteArray = Hex.decode(hwidHex)!!

    /** The actual Device Message; empty input becomes a single 0x00 byte. */
    val deviceMessage: ByteArray = SimpleBeaconFrame.deviceMessageBytes(deviceMessageHex)!!

    /** LINE Simple Beacon Frame as it goes into the Service Data AD. */
    val frame: ByteArray = SimpleBeaconFrame.buildFrame(hwid, deviceMessage)

    val frameHex: String = Hex.encode(frame)

    companion object {
        /**
         * Validates the user input and returns a config, or the first error.
         */
        fun newInstance(hwidHex: String, deviceMessageHex: String): Result<BeaconConfig> {
            val hwidError = SimpleBeaconFrame.hwidError(hwidHex)
            if (hwidError != null) return failure(hwidError)
            val dmError = SimpleBeaconFrame.deviceMessageError(deviceMessageHex)
            if (dmError != null) return failure(dmError)
            val dmHex = deviceMessageHex.filterNot { it.isWhitespace() }.uppercase()
                .ifEmpty { "" } // 空欄なら0x00が送られる
            val hwidNormalized = hwidHex.filterNot { it.isWhitespace() }.uppercase()
            return runCatching { BeaconConfig(hwidNormalized, dmHex) }
        }

        private fun failure(message: String): Result<BeaconConfig> =
            Result.failure(IllegalArgumentException(message))
    }
}
