package com.mastik.wifi_direct.tasks

import com.mastik.wifi_direct.transfer.Communicator
import com.mastik.wifi_direct.transfer.Communicator.Companion.MAGIC_FILE_BYTE
import com.mastik.wifi_direct.transfer.Communicator.Companion.MAGIC_STRING_BYTE
import com.mastik.wifi_direct.transfer.FileDescriptorTransferInfo
import com.mastik.wifi_direct.transfer.FileTransferProgressInfo
import trikita.log.Log
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.SocketException
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import java.util.function.Function
import kotlin.concurrent.withLock
import kotlin.math.min

open class SocketCommunicator() : Communicator {
    companion object {
        const val DEFAULT_BUFFER_SIZE = 32_768
    }

    private var mainOutStream: OutputStream? = null
    private var outTextStream: OutputStreamWriter? = null

    private val writeLock = ReentrantLock(true)
    private var loopStarted: Condition? = writeLock.newCondition()

    private val onMessageSend: Consumer<String> = Consumer<String> { message ->
        writeLock.withLock {
            loopStarted?.await()
            outTextStream?.let {
                val len = message.length
                Log.d("Sending message of $len bytes: ", message)
                try {
                    it.write(MAGIC_STRING_BYTE)
                    it.flush()
                    for (i in 0 until Int.SIZE_BYTES) mainOutStream!!.write(len shr (i * 8))
                    it.flush()
                    it.write(message)
                    it.flush()
                } catch (e: IOException) {
                    Log.e("Send message error: ", e)
                }
            }
        }
    }


    private val onFileSend: Consumer<FileDescriptorTransferInfo> =
        Consumer<FileDescriptorTransferInfo> { file ->
            Log.d("Sending file: ${file.name}")
            writeLock.withLock {
                loopStarted?.await()
                mainOutStream?.let {
                    val fileStream = DataInputStream(FileInputStream(file.descriptor))

                    var sentBytes: Long = 0
                    val sendingStart = System.currentTimeMillis()
                    var fileSize: Long = 0
                    try {
                        fileSize = fileStream.available().toLong() // For small files that will be correct

                        it.write(MAGIC_FILE_BYTE)
                        it.flush()
                        for (i in 0 until Int.SIZE_BYTES) mainOutStream!!.write(fileStream.available() shr (i * 8))
                        it.flush()

                        outTextStream!!.write(file.name.toCharArray())
                        outTextStream!!.write(0)
                        outTextStream!!.flush()

                        val arr = ByteArray(DEFAULT_BUFFER_SIZE)

                        while (fileStream.available() > 0) {
                            val toRead = min(fileStream.available(), DEFAULT_BUFFER_SIZE)
                            fileStream.readFully(arr, 0, toRead)
                            it.write(arr, 0, toRead)
                            sentBytes += toRead
                            fileSize = fileStream.available() + sentBytes
                            file.updateTransferProgress(
                                FileTransferProgressInfo(
                                    sentBytes,
                                    fileSize,
                                    sentBytes.toFloat() / (System.currentTimeMillis() - sendingStart) * 1000
                                )
                            )
                        }
                        it.flush()
                    } finally {
                        file.endTransferProgress(
                            FileTransferProgressInfo(
                                sentBytes,
                                fileSize,
                                sentBytes.toFloat() / (System.currentTimeMillis() - sendingStart) * 1000
                            )
                        )

                        try {
                            fileStream.close()
                        } catch (e: IOException) {
                            Log.e("File stream close error: ", e)
                        }
                    }
                }
            }
        }

    private var newMessageListener: Consumer<String>? = null
    private var newFileListener: Function<String, FileDescriptorTransferInfo?>? = null


    @Throws(IOException::class)
    fun readLoop(socket: Socket) {
        writeLock.withLock {
            loopStarted!!.signalAll()
            loopStarted = null

            mainOutStream = socket.getOutputStream()
            outTextStream = OutputStreamWriter(socket.getOutputStream(), Charset.forName("UTF-8"))
        }

        val rawStream = socket.getInputStream()
        val stream = InputStreamReader(socket.getInputStream())

        var messageBuff = CharBuffer.allocate(1024)
        val byteArray = ByteArray(4)

        while (socket.isConnected) {
            rawStream.read(byteArray, 0, 1)
            val magic = byteArray[0].toInt() and 0xFF
            rawStream.read(byteArray, 0, 4)
            var dataSize: Long = 0
            for (i in 0 until Int.SIZE_BYTES) dataSize += (byteArray[i].toInt() and 0xFF) shl (i * 8)
            if (magic == MAGIC_STRING_BYTE) {
                if (messageBuff.capacity() < dataSize)
                    messageBuff = CharBuffer.allocate(dataSize.toInt())
                stream.read(messageBuff)

                val message = messageBuff.position(0).toString().substring(0, dataSize.toInt())
                Log.d("Received $dataSize bytes: ", message)
                newMessageListener?.accept(message)
                messageBuff.clear()
                continue
            }
            if (magic == MAGIC_FILE_BYTE) {
                var nameLength = 0

                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                rawStream.read(buffer, 0, 1)
                while (buffer[nameLength].toInt() and 0xFF != 0) {
                    rawStream.read(buffer, ++nameLength, 1)
                }

                val fileName = String(buffer.copyOf(nameLength))
                messageBuff.clear()

                newFileListener?.apply(fileName).also {
                    if (it == null) {
                        while (dataSize > 0)
                            dataSize -= rawStream.skip(
                                min(
                                    dataSize.toInt(),
                                    DEFAULT_BUFFER_SIZE
                                ).toLong()
                            )
                        return@also
                    }

                    var total: Long = 0
                    val start = System.currentTimeMillis()

                    val fileStream = FileOutputStream(it.descriptor)
                    try {
                        var i = 0

                        while (dataSize > 0) {
                            val toRead = min(dataSize.toInt(), buffer.size)
                            rawStream.readNBytes(
                                buffer,
                                0,
                                toRead
                            )
                            dataSize -= toRead
                            fileStream.write(buffer, 0, toRead)

                            total += toRead
                            if (i % 15 == 0)
                                TaskExecutors.getCachedPool().execute {
                                    it.updateTransferProgress(
                                        FileTransferProgressInfo(
                                            total,
                                            dataSize + total,
                                            total.toFloat() / (System.currentTimeMillis() - start) * 1000
                                        )
                                    )
                                }
                            i++
                        }
                    } finally {
                        // Trigger end transfer listener
                        it.endTransferProgress(
                            FileTransferProgressInfo(
                                total,
                                dataSize + total,
                                total.toFloat() / (System.currentTimeMillis() - start) * 1000
                            )
                        )

                        fileStream.close()
                    }

                    Log.d("Successfully read file: ", fileName)
                }
                continue
            }
            Log.d("Unknown magic number $magic")
            throw SocketException("Unknown magic number")
        }
    }

    fun isBusy(): Boolean {
        return writeLock.isLocked
    }

    override fun getMessageSender(): Consumer<String> = onMessageSend
    override fun getFileSender(): Consumer<FileDescriptorTransferInfo> = onFileSend

    override fun setOnNewMessageListener(onNewMessage: Consumer<String>) {
        newMessageListener = onNewMessage
    }

    override fun setOnNewFileListener(onNewFile: Function<String, FileDescriptorTransferInfo?>) {
        newFileListener = onNewFile
    }
}