package es.thormando.unificado

import es.thormando.unificado.domain.*
import es.thormando.unificado.net.Router
import java.net.InetSocketAddress
import java.nio.*
import java.util.zip.CRC32
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

internal fun request(kind: Int, payload: ByteArray = byteArrayOf()): ByteArray {
    val b = ByteBuffer.allocate(20 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
    b.put("DSUC".toByteArray())
    b.putShort(1001)
    b.putShort((4 + payload.size).toShort())
    b.putInt(0)
    b.putInt(123)
    b.putInt(kind)
    b.put(payload)
    b.putInt(8, CRC32().apply { update(b.array()) }.value.toInt())
    return b.array()
}

class ProtocolTest {
    @Test
    fun framesMatchVersion14() {
        val frames =
            javaClass.getResourceAsStream("/v14-frames.hex")!!.bufferedReader().use {
                it.readLines()
            }
        assertEquals(30, frames.size)
        for (slot in 0..1) for (i in 0..14) {
            val keys =
                when (i) {
                    in 0..11 -> setOf(Key.entries[i])
                    12 -> Key.entries.toSet()
                    else -> emptySet()
                }
            val p =
                if (i == 14) Pad(slot, i + 1)
                else
                    Pad(
                        slot,
                        i + 1,
                        keys,
                        83 + i to 142 - i,
                        4294967296L + i,
                        Vector(1.25, -2.75, 9.5),
                        Vector(.5, -.25, 1.5),
                    )
            val expected =
                frames[slot * 15 + i].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            assertArrayEquals("Slot $slot sample $i", expected, Dsu.pad(p))
        }
    }

    @Test
    fun tapEdges() {
        val b = Buttons()
        b.touch(Key.A, true)
        assertTrue(Key.A in b.sample(100))
        b.touch(Key.A, false)
        b.touch(Key.A, true)
        b.touch(Key.A, false)
        b.touch(Key.B, true)
        assertEquals(setOf(Key.A, Key.B), b.sample(104))
        assertEquals(setOf(Key.B), b.sample(136))
        assertEquals(setOf(Key.A, Key.B), b.sample(154))
        assertEquals(setOf(Key.B), b.sample(190))
        b.clear()
        assertTrue(b.sample(191).isEmpty())
        b.touch(Key.CENTER, true)
        b.sample(200)
        b.touch(Key.CENTER, false)
        assertTrue(Key.CENTER in b.sample(299))
        assertTrue(b.sample(300).isEmpty())
    }

    @Test
    fun freezeRequiresOneUninterruptedHold() {
        val h = FreezeHold()
        assertTrue(h.begin(1, 100))
        assertFalse(h.consume(1099))
        assertFalse(h.begin(2, 1000))
        h.end(2)
        assertTrue(h.consume(1100))
        assertFalse(h.consume(4100))
        h.begin(2, 5000)
        h.move(2, false)
        assertFalse(h.consume(7000))
        h.begin(3, 8000)
        h.cancel()
        assertFalse(h.consume(10000))
        h.begin(1, 11000)
        h.end(1)
        assertFalse(h.consume(14000))
    }

    @Test
    fun poseAndFallback() {
        val aim = Aim()
        for (i in 0..199) {
            val a = i * .014
            aim.rotation(0.0, 0.0, sin(a), cos(a))
            aim.center()
            assertEquals(128 to 128, aim.axes())
            aim.rotation(0.0, 0.0, sin(a - .12), cos(a - .12))
            assertTrue(aim.axes().first > 128)
        }
        val fallback = Aim()
        fallback.gyro(Vector(z = -2.0), 1_000_000_000)
        fallback.gyro(Vector(z = -2.0), 1_020_000_000)
        assertTrue(fallback.axes().first > 128)
        fallback.center()
        assertEquals(128 to 128, fallback.axes())
    }

    @Test
    fun routerIsolationAndExpiry() {
        val r = Router("123456")
        val phone = InetSocketAddress("127.0.0.2", 44000)
        val other = InetSocketAddress("127.0.0.3", 44000)
        val dolphin = InetSocketAddress("127.0.0.1", 44001)
        val all = InetSocketAddress("127.0.0.1", 44002)
        val token = "THORMANDO/123456".toByteArray()
        val data = Dsu.pad(Pad(0, 8, setOf(Key.A)))
        assertTrue(r.receive(0, phone, data, 1).isEmpty())
        assertTrue(r.receive(0, phone, "THORMANDO/000000".toByteArray(), 1).isEmpty())
        assertArrayEquals(token, r.receive(0, phone, token, 1).single().bytes)
        assertTrue(
            r.receive(2, dolphin, request(Dsu.DATA, byteArrayOf(2, 0) + Dsu.mac(0)), 1).isEmpty()
        )
        r.receive(2, all, request(Dsu.DATA, ByteArray(8)), 1)
        assertEquals(setOf(dolphin, all), r.receive(0, phone, data, 2).map { it.target }.toSet())
        assertTrue(r.receive(0, other, token, 4).isEmpty())
        r.receive(1, other, token, 10)
        assertEquals(all, r.receive(1, other, Dsu.pad(Pad(1, 9)), 11).single().target)
        assertTrue(r.receive(1, phone, data, 20).isEmpty())
        val bad = data.clone().apply { this[50] = 1 }
        assertTrue(r.receive(0, phone, bad, 30).isEmpty())
        val info = r.receive(2, dolphin, request(Dsu.INFO, byteArrayOf(4, 0, 0, 0, 0, 1, 2, 3)), 31)
        assertEquals(listOf(2, 2, 0, 0), info.map { it.bytes[21].toInt() })
        val releases = r.expire(652)
        assertEquals(2, releases.size)
        assertEquals(0, releases.first().bytes[49].toInt())
        assertFalse(r.connected(0))
        assertTrue(r.connected(1))
        assertTrue(r.receive(0, other, token, 660).isNotEmpty())
        r.expire(7000)
        assertTrue(r.receive(0, other, data, 7001).isEmpty())
    }
}
