package com.mastik.wifi_direct.tasks

import com.mastik.wifi_direct.transfer.Communicator
import com.mastik.wifi_direct.transfer.FileDescriptorTransferInfo
import trikita.log.Log
import java.util.LinkedList
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.function.Function
import kotlin.concurrent.withLock

class MultiConnectTask(
    private val host: String,
    private val defaultPort: Int
) : Communicator {

    private val communicatorsLock: ReadWriteLock = ReentrantReadWriteLock()
    private val communicators: LinkedList<ConnectTask> = LinkedList()

    private var newFileListener: Function<String, FileDescriptorTransferInfo?>? = null
    private var newMessageListener: Consumer<String>? = null

    private fun createCommunicator(): Communicator {
        communicatorsLock.writeLock().withLock {
            val newCommunicator = ConnectTask(host, defaultPort)
            newCommunicator.setOnNewMessageListener{
                newMessageListener?.accept(it)
            }
            newCommunicator.setOnNewFileListener{
                return@setOnNewFileListener newFileListener?.apply(it)
            }
            communicators.add(newCommunicator)
            try {
                TaskExecutors.getCachedPool().execute {
                    newCommunicator.run()

                    communicatorsLock.writeLock().withLock {
                        communicators.remove(newCommunicator)
                    }
                }
            } catch (e: Exception) {
                Log.e("Connection to WIFI P2P client error: ", e)
            }
            return newCommunicator
        }
    }
    override fun getFileSender(): Consumer<FileDescriptorTransferInfo> =
        Consumer { transferInfo ->
            nextFreeOrNewCommunicator().getFileSender().accept(transferInfo)
        }

    override fun getMessageSender(): Consumer<String> =
        Consumer { message ->
            nextFreeOrNewCommunicator().getMessageSender().accept(message)
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

    private fun nextFreeOrNewCommunicator(): Communicator {
        var communicator: Communicator? = null
        communicatorsLock.readLock().withLock {
            communicator = communicators.find { e -> !e.isBusy() }
        }
        if(communicator == null)
            communicator = createCommunicator()

        return communicator!!
    }

}