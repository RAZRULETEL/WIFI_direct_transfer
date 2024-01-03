package com.mastik.wifi_direct.tasks

import com.mastik.wifi_direct.transfer.Communicator
import com.mastik.wifi_direct.transfer.FileDescriptorTransferInfo
import trikita.log.Log
import java.net.BindException
import java.net.ServerSocket
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.function.Function
import kotlin.concurrent.withLock

class ServerStartTask(
    private val defaultPort: Int,
) : Communicator, Runnable {

    companion object {
        const val MAX_PORT_OFFSET = 10

        private val isServerRunning: AtomicBoolean = AtomicBoolean(false)

        fun isServerRunning(): Boolean = isServerRunning.get()
    }

    private val communicatorsLock: ReadWriteLock = ReentrantReadWriteLock()
    private val communicators: MutableMap<String, LinkedList<Communicator>> = mutableMapOf()

    private var newFileListener: Function<String, FileDescriptorTransferInfo?>? = null
    private var newMessageListener: Consumer<String>? = null

    private var newClientListener: Consumer<String>? = null

    override fun run() {
        if (isServerRunning.get())
            throw IllegalStateException("Another instance of Server already running")

        var server: ServerSocket? = null
        var portOffset = 0
        try {
            while (server == null) {
                if (portOffset >= MAX_PORT_OFFSET) {
                    Log.e("Start socket listener error, port overflow")
                    return
                }
                try {
                    server = ServerSocket(defaultPort + portOffset++)
                } catch (e: BindException) {
                    e.printStackTrace()
                }
            }
            isServerRunning.set(!server.isClosed)
        } catch (e: IllegalArgumentException) {
            Log.e("Start socket listener error, port $defaultPort is invalid: ", e)
            return
        } catch (e: Exception) {
            Log.e("Start socket listener unexpected error: ", e)
            return
        }



        try {
            while (!server.isClosed) {
                val client = server.accept()
                TaskExecutors.getCachedPool().execute {
                    newClientListener?.accept(client.inetAddress.hostAddress)

                    val newClient = SocketCommunicator()
                    newClient.setOnNewMessageListener() {
                        newMessageListener?.accept(it)
                    }
                    newClient.setOnNewFileListener() {
                        return@setOnNewFileListener newFileListener?.apply(it)
                    }
                    communicatorsLock.writeLock().withLock {
                        communicators.getOrPut(client.inetAddress.hostAddress!!) {
                            return@getOrPut LinkedList()
                        }.add(newClient)
                    }
                    try {
                        newClient.readLoop(client)
                    } catch (_: Exception) {
                    } finally {
                        communicatorsLock.writeLock().withLock {
                            communicators[client.inetAddress.hostAddress!!]?.remove(newClient)
                        }
                    }
                }
            }
            server.close()
        } catch (e: Exception) {
            Log.e("Server stopped with error: ", e)
        } finally {
            communicators.clear()
            isServerRunning.set(false)
        }
    }

    override fun getFileSender(): Consumer<FileDescriptorTransferInfo> =
        Consumer { transferInfo ->
            val fileSenders: MutableList<Consumer<FileDescriptorTransferInfo>> = mutableListOf()
            communicatorsLock.writeLock().withLock {
                communicators.forEach {
                    if (it.value.size <= 0)
                        return@forEach
                    fileSenders.add(it.value[0].getFileSender())
                    it.value.addLast(it.value.removeFirst())
                }
            }
            for (sender in fileSenders)
                sender.accept(transferInfo)
        }

    override fun getMessageSender(): Consumer<String> =
        Consumer { message ->
            communicatorsLock.readLock().withLock {
                communicators.forEach {
                    if (it.value.size <= 0)
                        return@forEach
                    it.value[0].getMessageSender().accept(message)
                }
            }
        }

    override fun setOnNewFileListener(onNewFile: Function<String, FileDescriptorTransferInfo?>) {
        newFileListener = onNewFile
    }

    override fun setOnNewMessageListener(onNewMessage: Consumer<String>) {
        newMessageListener = onNewMessage
    }

    fun getActiveConnections(): Int {
        return communicators.values.sumOf { e -> e.size }
    }

    fun getClientsAddresses(): List<String> {
        return communicators.keys.filter { e -> communicators[e]!!.size > 0 }
    }

    fun setOnNewClientListener(newClientListener: Consumer<String>) {
        this.newClientListener = newClientListener
    }
}