package com.mastik.wifi_direct.transfer

/**
 * @param bytesProgress bytes transferred so far
 * @param bytesTotal total bytes to transfer
 * @param currentSpeed current transfer speed in bytes per second
 */
class FileTransferProgressInfo(val bytesProgress: Long, val bytesTotal: Long, val currentSpeed: Float) {

    /**
     * Estimated left time of arrival in seconds
     */
    val ETA: Float = (bytesTotal - bytesProgress) / currentSpeed
}