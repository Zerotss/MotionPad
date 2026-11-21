package es.thormando.unificado.net

import java.io.Closeable
import java.io.IOException
import java.net.*
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

class Handset(
    code: String,
    slot: Int,
    private val frame: () -> ByteArray,
    private val targets: () -> List<InetAddress> = ::broadcastTargets,
    private val status: (String) -> Unit,
) : Closeable {
    private val port = 38761 + slot
    private val token = "THORMANDO/$code".toByteArray()
    @Volatile private var running = true
    @Volatile private var peer: InetAddress? = null
    @Volatile private var probeSocket: DatagramSocket? = null
    @Volatile private var dataSocket: DatagramSocket? = null
    private val probe =
        thread(name = "Thor-discovery") {
            var index = 0
            while (running) {
                try {
                    DatagramSocket().use { socket ->
                        probeSocket = socket
                        socket.broadcast = true
                        socket.soTimeout = 700
                        while (running) {
                            val target =
                                peer ?: targets().let { it[Math.floorMod(index++, it.size)] }
                            val start = System.nanoTime()
                            socket.send(DatagramPacket(token, token.size, target, port))
                            val ack = DatagramPacket(ByteArray(64), 64)
                            socket.receive(ack)
                            if (
                                ack.port == port && ack.data.copyOf(ack.length).contentEquals(token)
                            ) {
                                peer = ack.address
                                status("Connected · Ping ${(System.nanoTime()-start)/1_000_000} ms")
                            }
                            Thread.sleep(1000)
                        }
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (_: IOException) {
                    peer = null
                    if (running) status("Looking for your Thor on Wi-Fi…")
                    try {
                        Thread.sleep(150)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
    private val sender =
        thread(name = "Thor-pad") {
            while (running) {
                try {
                    DatagramSocket().use { socket ->
                        dataSocket = socket
                        while (running) {
                            peer?.let {
                                val bytes = frame()
                                socket.send(DatagramPacket(bytes, bytes.size, it, port))
                            }
                            LockSupport.parkNanos(6_000_000L)
                        }
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (_: IOException) {
                    try {
                        Thread.sleep(150)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }

    fun inputChanged() = LockSupport.unpark(sender)

    override fun close() {
        running = false
        probeSocket?.close()
        dataSocket?.close()
        probe.interrupt()
        sender.interrupt()
    }
}

private fun broadcastTargets(): List<InetAddress> {
    val found = linkedSetOf<InetAddress>()
    NetworkInterface.getNetworkInterfaces()
        ?.toList()
        ?.filter { it.isUp && !it.isLoopback }
        ?.forEach { ni -> ni.interfaceAddresses.mapNotNullTo(found) { it.broadcast } }
    found.add(InetAddress.getByName("255.255.255.255"))
    return found.toList()
}
