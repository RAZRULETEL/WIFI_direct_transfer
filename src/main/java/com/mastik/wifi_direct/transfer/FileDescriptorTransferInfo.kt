package com.mastik.wifi_direct.transfer

import java.io.FileDescriptor
import java.util.function.Consumer

class FileDescriptorTransferInfo(val descriptor: FileDescriptor, val name: String) {
    companion object{
        const val STATE_CREATED = 0
        const val STATE_TRANSFER_IN_PROGRESS = 1
        const val STATE_TRANSFER_COMPLETED = 2
        const val STATE_TRANSFER_ERROR = 3
    }
    private var transferState: Int = STATE_CREATED

    private val progressListeners = mutableListOf<Consumer<FileTransferProgressInfo>>()
    var onTransferStartListener: Consumer<FileTransferProgressInfo>? = null
    var onTransferEndListener: Consumer<FileTransferProgressInfo>? = null

    fun addProgressListener(progressListener: Consumer<FileTransferProgressInfo>){
        progressListeners.add(progressListener)
    }

    fun updateTransferProgress(progress: FileTransferProgressInfo){
        if(progress.bytesProgress > 0 && transferState == STATE_CREATED){
            transferState = STATE_TRANSFER_IN_PROGRESS
            onTransferStartListener?.accept(progress)
        }

        progressListeners.forEach { it.accept(progress) }

        if(progress.bytesProgress == progress.bytesTotal && transferState == STATE_TRANSFER_IN_PROGRESS){
            endTransferProgress(progress)
        }
    }

    fun endTransferProgress(progress: FileTransferProgressInfo){
        transferState = if(progress.bytesProgress == progress.bytesTotal) STATE_TRANSFER_COMPLETED else STATE_TRANSFER_ERROR
        onTransferEndListener?.accept(progress)
    }
}