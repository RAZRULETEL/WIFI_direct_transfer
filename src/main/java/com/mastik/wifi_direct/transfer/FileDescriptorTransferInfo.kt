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
    var transferState: Int = STATE_CREATED
        private set

    private val progressListeners = mutableListOf<Consumer<FileTransferProgressInfo>>()
    private val transferEndListeners = mutableListOf<Consumer<FileTransferProgressInfo>>()

    fun addProgressListener(progressListener: Consumer<FileTransferProgressInfo>){
        progressListeners.add(progressListener)
    }

    fun removeProgressListener(progressListener: Consumer<FileTransferProgressInfo>){
        progressListeners.remove(progressListener)
    }

    fun addTransferEndListener(endListener: Consumer<FileTransferProgressInfo>){
        transferEndListeners.add(endListener)
    }

    fun removeTransferEndListener(endListener: Consumer<FileTransferProgressInfo>){
        transferEndListeners.remove(endListener)
    }

    fun updateTransferProgress(progress: FileTransferProgressInfo){
        progressListeners.forEach { it.accept(progress) }

        if(progress.bytesProgress == progress.bytesTotal && transferState == STATE_TRANSFER_IN_PROGRESS){
            endTransferProgress(progress)
        }
    }

    fun endTransferProgress(progress: FileTransferProgressInfo){
        transferState = if(progress.bytesProgress == progress.bytesTotal) STATE_TRANSFER_COMPLETED else STATE_TRANSFER_ERROR
        transferEndListeners.forEach{ it.accept(progress) }
    }
}