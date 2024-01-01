package com.mastik.wifi_direct.tasks

import com.mastik.wifi_direct.transfer.Communicator
import com.mastik.wifi_direct.transfer.FileDescriptorTransferInfo
import trikita.log.Log
import java.util.LinkedList
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.function.Function

class MultiConnectTask(
    private val host: String,
    private val defaultPort: Int
) : Communicator {

    private val communicatorsLock: ReadWriteLock = ReentrantReadWriteLock()
    private val communicators: LinkedList<ConnectTask> = LinkedList()

    private var newFileListener: Function<String, FileDescriptorTransferInfo?>? = null
    private var newMessageListener: Consumer<String>? = null

    private fun createCommunicator() {
        communicatorsLock.writeLock().lock()
        try {
            val newCommunicator = ConnectTask(host, defaultPort)
            newCommunicator.setOnNewMessageListener {
                newMessageListener?.accept(it)
            }
            newCommunicator.setOnNewFileListener {
                return@setOnNewFileListener newFileListener?.apply(it)
            }
            communicators.addFirst(newCommunicator)
            try {
                TaskExecutors.getCachedPool().execute {
                    newCommunicator.run()

                    communicatorsLock.writeLock().lock()
                    communicators.remove(newCommunicator)
                    communicatorsLock.writeLock().unlock()
                }
            } catch (e: Exception) {
                Log.e("Connection to WIFI P2P client error: ", e)
            }
        } finally {
            communicatorsLock.writeLock().unlock()
        }
    }

    override fun getFileSender(): Consumer<FileDescriptorTransferInfo> =
        Consumer { transferInfo ->
            if (communicators.size <= 0 || communicators.all { e -> e.isBusy() })
                createCommunicator()

            val communicator: Consumer<FileDescriptorTransferInfo>
            communicatorsLock.writeLock().lock()
            try {
                communicator = communicators[0].getFileSender()
                communicators.addLast(communicators.removeFirst())
            } finally {
                communicatorsLock.writeLock().unlock()
            }
            communicator.accept(transferInfo)
        }

    override fun getMessageSender(): Consumer<String> =
        Consumer { message ->
            if (communicators.size <= 0)
                createCommunicator()

            communicatorsLock.readLock().lock()
            val cmc = communicators[0].getMessageSender()
            communicatorsLock.readLock().unlock()
            cmc.accept(message)
        }

    override fun setOnNewFileListener(onNewFile: Function<String, FileDescriptorTransferInfo?>) {
        newFileListener = onNewFile
    }

    override fun setOnNewMessageListener(onNewMessage: Consumer<String>) {
        newMessageListener = onNewMessage
    }

    fun destroy() {
        communicators.forEach { e ->
            e.close()
        }
    }

    fun getActiveConnections(): Int {
        return communicators.size
    }
}