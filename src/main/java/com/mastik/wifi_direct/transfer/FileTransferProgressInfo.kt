package com.mastik.wifi_direct.transfer

class FileTransferProgressInfo(val bytesProgress: Long, val bytesTotal: Long, val currentSpeed: Float) {
    val ETA: Float = (bytesTotal - bytesProgress) / currentSpeed
}