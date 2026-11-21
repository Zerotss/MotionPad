package es.thormando.unificado.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

object Dsu {
    const val VERSION = 0x100000
    const val INFO = VERSION + 1
    const val DATA = VERSION + 2

    private fun buffer(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    fun word(bytes: ByteArray, offset: Int) = buffer(bytes).getInt(offset)

    fun mac(slot: Int) = byteArrayOf(2, 84, 72, 79, 82, (slot + 1).toByte())

    private fun header(kind: Int, size: Int) =
        buffer(ByteArray(size)).apply {
            put("DSUS".toByteArray(Charsets.US_ASCII))
            putShort(1001)
            putShort((size - 16).toShort())
            putInt(0)
            putInt(0x524f4854)
            putInt(kind)
        }

    private fun seal(b: ByteBuffer): ByteArray {
        b.putInt(8, CRC32().apply { update(b.array()) }.value.toInt())
        return b.array()
    }

    fun valid(bytes: ByteArray, client: Boolean): Boolean {
        if (bytes.size < 20) return false
        val b = buffer(bytes)
        if (
            b.getInt(0) != (if (client) 0x43555344 else 0x53555344) ||
                b.getShort(4).toInt() != 1001 ||
                b.getShort(6).toInt() != bytes.size - 16
        )
            return false
        val crc =
            CRC32().apply {
                update(bytes, 0, 8)
                repeat(4) { update(0) }
                update(bytes, 12, bytes.size - 12)
            }
        return crc.value.toInt() == b.getInt(8)
    }

    private fun port(b: ByteBuffer, slot: Int, active: Boolean) {
        b.put(slot.toByte())
        if (active) {
            b.put(byteArrayOf(2, 2, 2))
            b.put(mac(slot))
            b.put(5)
        }
    }

    fun version() = seal(header(VERSION, 24).apply { putShort(1001) })

    fun info(slot: Int, active: Boolean) = seal(header(INFO, 32).apply { port(this, slot, active) })

    fun pad(p: Pad): ByteArray {
        val b = header(DATA, 100)
        port(b, p.slot, true)
        b.put(31, 1)
        b.putInt(32, p.sequence)
        for (key in p.keys) {
            b.put(key.offset, (b.get(key.offset).toInt() or key.mask).toByte())
            if (key.analog >= 0) b.put(key.analog, 255.toByte())
        }
        b.put(40, 128.toByte())
        b.put(41, 128.toByte())
        b.put(42, p.aim.first.toByte())
        b.put(43, p.aim.second.toByte())
        b.putLong(68, p.micros)
        b.position(76)
        val (ax, ay, az) = p.accel
        val (gx, gy, gz) = p.gyro
        for (n in
            doubleArrayOf(
                -ax / 9.80665,
                -az / 9.80665,
                ay / 9.80665,
                Math.toDegrees(gx),
                -Math.toDegrees(gz),
                Math.toDegrees(gy),
            )) b.putFloat(n.toFloat())
        return seal(b)
    }
}
