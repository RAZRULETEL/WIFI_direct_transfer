package com.mastik.wifi_direct.transfer

import java.util.function.Function
import java.util.function.Consumer

interface Communicator {
    abstract fun getMessageSender(): Consumer<String>
    abstract fun setOnNewMessageListener(onNewMessage: Consumer<String>)
    abstract fun getFileSender(): Consumer<FileDescriptorTransferInfo>
    abstract fun setOnNewFileListener(onNewFile: Function<String, FileDescriptorTransferInfo?>)

    companion object {
        const val MAGIC_STRING_BYTE = 0x4D
        const val MAGIC_FILE_BYTE = 0x46
    }
}