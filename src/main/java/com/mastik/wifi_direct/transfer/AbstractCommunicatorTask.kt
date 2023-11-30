package com.mastik.wifi_direct.transfer

import com.mastik.wifi_direct.tasks.SocketCommunicator
import java.util.function.Consumer
import java.util.function.Function

abstract class AbstractCommunicatorTask: Runnable, Communicator {
    protected val communicator: SocketCommunicator = SocketCommunicator()


    override fun getMessageSender() = communicator.getMessageSender()
    override fun getFileSender() = communicator.getFileSender()

    override fun setOnNewMessageListener(onNewMessage: Consumer<String>) = communicator.setOnNewMessageListener(onNewMessage)
    override fun setOnNewFileListener(onNewFile: Function<String, FileDescriptorTransferInfo>) = communicator.setOnNewFileListener(onNewFile)
}