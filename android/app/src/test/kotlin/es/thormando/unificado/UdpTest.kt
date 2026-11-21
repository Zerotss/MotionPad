package es.thormando.unificado

import es.thormando.unificado.domain.*
import es.thormando.unificado.net.Handset
import es.thormando.unificado.net.Receiver
import java.net.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class UdpTest {
    @Test
    fun nativePhoneDiscoversAndSendsItsGamepad() {
        val listening = CountDownLatch(1)
        val connected = CountDownLatch(1)
        val receiver = Receiver("123456") { if (it.message.contains("P2")) listening.countDown() }
        val local = InetAddress.getByName("127.0.0.1")
        try {
            assertTrue(listening.await(4, TimeUnit.SECONDS))
            DatagramSocket().use { client ->
                client.soTimeout = 3500
                val registration = request(Dsu.DATA, byteArrayOf(1, 1) + ByteArray(6))
                client.send(DatagramPacket(registration, registration.size, local, 26760))
                val bytes = Dsu.pad(Pad(1, 146, setOf(Key.B)))
                Handset("123456", 1, { bytes }, { listOf(local) }) {
                        if (it.startsWith("Connected")) connected.countDown()
                    }
                    .use {
                        assertTrue(connected.await(3, TimeUnit.SECONDS))
                        val packet = DatagramPacket(ByteArray(256), 256)
                        client.receive(packet)
                        assertArrayEquals(bytes, packet.data.copyOf(packet.length))
                        assertEquals(26760, packet.port)
                    }
            }
        } finally {
            receiver.close()
        }
    }

    @Test
    fun twoPhonesAndTwoDolphinClientsOnRealSockets() {
        val bound = CountDownLatch(1)
        val receiver = Receiver("654321") { if (it.message.contains("P2")) bound.countDown() }
        fun send(s: DatagramSocket, p: ByteArray, port: Int) =
            s.send(DatagramPacket(p, p.size, InetAddress.getByName("127.0.0.1"), port))
        fun read(s: DatagramSocket): ByteArray {
            val packet = DatagramPacket(ByteArray(256), 256)
            s.receive(packet)
            return packet.data.copyOf(packet.length)
        }
        val phones = List(2) { DatagramSocket().apply { soTimeout = 2000 } }
        val clients = List(2) { DatagramSocket().apply { soTimeout = 2000 } }
        try {
            assertTrue("Receiver failed to bind", bound.await(3, TimeUnit.SECONDS))
            for (i in 0..1) {
                val token = "THORMANDO/654321".toByteArray()
                send(phones[i], token, 38761 + i)
                assertArrayEquals(token, read(phones[i]))
                send(
                    clients[i],
                    request(Dsu.DATA, byteArrayOf(1, i.toByte()) + ByteArray(6)),
                    26760,
                )
                send(clients[i], request(Dsu.VERSION), 26760)
                val version = read(clients[i])
                assertEquals(24, version.size)
                assertTrue(Dsu.valid(version, false))
                assertEquals(Dsu.VERSION, Dsu.word(version, 16))
            }
            for (i in 0..1) send(phones[i], Dsu.pad(Pad(i, 25, setOf(Key.A))), 38761 + i)
            for (i in 0..1) {
                val p = read(clients[i])
                assertEquals(i, p[20].toInt())
                assertEquals(255, p[49].toInt() and 255)
                assertTrue(Dsu.valid(p, false))
            }
            for (i in 0..1) assertEquals(0, read(clients[i])[49].toInt())
        } finally {
            (phones + clients).forEach { it.close() }
            receiver.close()
        }
    }
    @Test
    fun measuresPeriodicAndImmediateInputWithOneAndTwoSenders() {
        val local = InetAddress.getByName("127.0.0.1")
        val masks = java.util.concurrent.LinkedBlockingQueue<Int>()
        val ready = CountDownLatch(1)
        Receiver("123456") { update ->
            masks.add(update.connectedMask)
            if (update.message.contains("P2")) ready.countDown()
        }.use {
            assertTrue(ready.await(4, TimeUnit.SECONDS))
            fun awaitMask(wanted: Int) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
                while (System.nanoTime() < deadline) {
                    if (masks.poll(100, TimeUnit.MILLISECONDS) == wanted) return
                }
                fail("Missing receiver connection mask $wanted")
            }
            val marker = java.util.concurrent.atomic.AtomicInteger()
            fun phone(slot: Int) = Handset("123456", slot, {
                Dsu.pad(Pad(slot, if (slot == 0) marker.get() else 0, setOf(Key.A)))
            }, { listOf(local) }) {}
            DatagramSocket().use { client ->
                client.soTimeout = 2000
                phone(0).use { first ->
                    awaitMask(1)
                    fun measure(players: Int) {
                        val register = request(Dsu.DATA, byteArrayOf(1, 0) + ByteArray(6))
                        client.send(DatagramPacket(register, register.size, local, 26760))
                        val periodic = mutableListOf<Long>()
                        val immediate = mutableListOf<Long>()
                        val packet = DatagramPacket(ByteArray(100), 100)
                        for (i in 0 until 160) {
                            Thread.sleep((i % 5 + 1).toLong())
                            val start = System.nanoTime()
                            val expected = marker.incrementAndGet()
                            val wake = i % 2 == 1
                            if (wake) first.inputChanged()
                            do {
                                client.receive(packet)
                                assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2))
                            } while (Dsu.word(packet.data, 32) != expected)
                            (if (wake) immediate else periodic).add(System.nanoTime() - start)
                        }
                        for ((name, samples) in listOf("periodic" to periodic, "wake" to immediate)) {
                            samples.sort()
                            println("slots=$players mode=$name n=${samples.size} median_us=${samples[39]/1000} p95_us=${samples[75]/1000}")
                        }
                    }
                    measure(1)
                    phone(1).use {
                        awaitMask(3)
                        measure(2)
                    }
                    awaitMask(1)
                }
                awaitMask(0)
                phone(1).use { awaitMask(2) }
                awaitMask(0)
            }
        }
    }
}
