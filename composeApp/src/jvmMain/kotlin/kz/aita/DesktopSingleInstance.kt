package kz.aita

import java.awt.Frame
import java.awt.EventQueue
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlin.concurrent.thread

/** OS file lock is released on crashes too. Repeated launch only signals the existing window. */
internal class DesktopInstanceLease private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
    private val listener: ServerSocket
) : AutoCloseable {
    override fun close() { runCatching { listener.close() }; runCatching { lock.release() }; channel.close() }
    companion object {
        fun acquire(directory: File, onActivate: () -> Unit): DesktopInstanceLease? {
            check(directory.isDirectory || directory.mkdirs()) { "Cannot open AITA data directory" }
            val path = File(directory, "application.lock").toPath()
            val channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
            val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
            if (lock == null) {
                try {
                    repeat(10) {
                        val data = ByteBuffer.allocate(128); channel.read(data, 0); data.flip()
                        val parts = Charsets.UTF_8.decode(data).toString().trim().split(':')
                        val port = parts.firstOrNull()?.toIntOrNull()
                        if (port != null && parts.size == 2) {
                            val signalled = runCatching {
                                Socket().use { socket ->
                                    socket.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), 250)
                                    socket.getOutputStream().write((parts[1] + "\n").toByteArray())
                                }
                            }.isSuccess
                            if (signalled) return null
                        }
                        Thread.sleep(100)
                    }
                    return null // An existing owner still owns data; never start another writer.
                } finally { channel.close() }
            }
            try {
                val listener = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
                val token = UUID.randomUUID().toString()
                channel.truncate(0); channel.write(ByteBuffer.wrap("${listener.localPort}:$token".toByteArray()), 0); channel.force(true)
                val lease = DesktopInstanceLease(channel, lock, listener)
                thread(name = "AITA-window-activation", isDaemon = true) {
                    while (!listener.isClosed) runCatching {
                        listener.accept().use { peer ->
                            peer.soTimeout = 500
                            val bytes = peer.getInputStream().readNBytes(token.length + 1)
                            if (bytes.toString(Charsets.UTF_8) == "$token\n") onActivate()
                        }
                    }
                }
                return lease
            } catch (error: Exception) { lock.release(); channel.close(); throw error }
        }
    }
}

internal object DesktopSingleInstance {
    private var lease: DesktopInstanceLease? = null
    fun acquire(directory: File): Boolean {
        lease = DesktopInstanceLease.acquire(directory) {
            EventQueue.invokeLater {
                Frame.getFrames().filter { it.isDisplayable && it.title == "AITA" }.forEach {
                    it.extendedState = it.extendedState and Frame.ICONIFIED.inv()
                    it.isVisible = true; it.toFront(); it.requestFocus()
                }
            }
        } ?: return false
        Runtime.getRuntime().addShutdownHook(Thread { lease?.close() })
        return true
    }
}
