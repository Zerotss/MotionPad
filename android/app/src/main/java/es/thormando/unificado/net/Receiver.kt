package es.thormando.unificado.net

import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import kotlin.concurrent.thread

data class ReceiverStatus(val message: String, val connectedMask: Int = 0)

class Receiver(private val code: String, private val status: (ReceiverStatus) -> Unit) : Closeable {
    @Volatile private var running = true
    @Volatile private var selector: Selector? = null
    private val worker =
        thread(name = "Thor-DSU") {
            while (running) {
                try {
                    serve()
                } catch (e: IOException) {
                    if (running) {
                        status(ReceiverStatus("Could not open the connection. Retrying…"))
                        try {
                            Thread.sleep(750)
                        } catch (_: InterruptedException) {}
                    }
                }
            }
        }

    private fun serve() {
        val router = Router(code)
        val channels = mutableListOf<DatagramChannel>()
        Selector.open().use { sel ->
            selector = sel
            try {
                val addresses =
                    listOf(
                        InetSocketAddress(38761),
                        InetSocketAddress(38762),
                        InetSocketAddress("127.0.0.1", 26760),
                    )
                for ((index, address) in addresses.withIndex()) {
                    val ch = DatagramChannel.open()
                    channels.add(ch)
                    ch.configureBlocking(false)
                    ch.socket().bind(address)
                    ch.register(sel, SelectionKey.OP_READ, index)
                }
                fun send(replies: List<Router.Reply>) {
                    for (reply in replies) channels[reply.channel].send(
                        ByteBuffer.wrap(reply.bytes),
                        reply.target,
                    )
                }
                val buffer = ByteBuffer.allocate(2048)
                var previous = -1
                while (running) {
                    sel.select(80)
                    val now = System.nanoTime() / 1_000_000
                    send(router.expire(now))
                    val keys = sel.selectedKeys().iterator()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        keys.remove()
                        buffer.clear()
                        val i = k.attachment() as Int
                        val from = channels[i].receive(buffer) as? InetSocketAddress ?: continue
                        send(router.receive(i, from, buffer.array().copyOf(buffer.position()), now))
                    }
                    val mask = (if (router.connected(0)) 1 else 0) or (if (router.connected(1)) 2 else 0)
                    if (mask != previous) {
                        val message = (0..1).joinToString("   ·   ") {
                            "P${it+1} " + if (router.connected(it)) "connected" else "waiting"
                        }
                        status(ReceiverStatus(message, mask))
                        previous = mask
                    }
                }
            } finally {
                channels.forEach { runCatching { it.close() } }
                selector = null
            }
        }
    }

    override fun close() {
        running = false
        selector?.wakeup()
        worker.interrupt()
    }
}
