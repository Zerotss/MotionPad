package es.thormando.unificado.net

import es.thormando.unificado.domain.*
import java.net.InetAddress
import java.net.InetSocketAddress

/** All calls happen on the receiver's single network thread. */
class Router(code: String) {
    data class Reply(val channel: Int, val target: InetSocketAddress, val bytes: ByteArray)

    private class Slot {
        var owner: InetAddress? = null
        var lastFrame: Long? = null
        var sequence = 0
        val clients = mutableMapOf<InetSocketAddress, Long>()
    }

    private val token = "THORMANDO/$code".toByteArray()
    private val slots = List(2) { Slot() }

    fun connected(index: Int) = slots[index].lastFrame != null

    fun receive(channel: Int, from: InetSocketAddress, bytes: ByteArray, now: Long): List<Reply> {
        if (channel == 2) return dolphin(from, bytes, now)
        val slot = slots[channel]
        if (bytes.contentEquals(token)) {
            if (connected(channel) && slot.owner != from.address) return emptyList()
            slot.owner = from.address
            return listOf(Reply(channel, from, token))
        }
        if (
            slot.owner != from.address ||
                bytes.size != 100 ||
                bytes[20].toInt() != channel ||
                !Dsu.valid(bytes, false) ||
                Dsu.word(bytes, 16) != Dsu.DATA
        )
            return emptyList()
        slot.lastFrame = now
        slot.sequence = Dsu.word(bytes, 32)
        return slot.clients.keys.map { Reply(2, it, bytes) }
    }

    private fun dolphin(from: InetSocketAddress, bytes: ByteArray, now: Long): List<Reply> {
        if (!from.address.isLoopbackAddress || !Dsu.valid(bytes, true)) return emptyList()
        when (Dsu.word(bytes, 16)) {
            Dsu.VERSION -> return listOf(Reply(2, from, Dsu.version()))
            Dsu.INFO -> {
                if (bytes.size < 24) return emptyList()
                val count = Dsu.word(bytes, 20)
                if (count !in 1..4 || bytes.size < 24 + count) return emptyList()
                return (0 until count)
                    .map { bytes[24 + it].toInt() }
                    .filter { it in 0..3 }
                    .distinct()
                    .map { Reply(2, from, Dsu.info(it, it < 2 && connected(it))) }
            }
            Dsu.DATA -> {
                if (bytes.size < 28) return emptyList()
                val flags = bytes[20].toInt()
                for ((i, s) in slots.withIndex()) {
                    if (
                        flags == 0 ||
                            flags and 1 != 0 && bytes[21].toInt() == i ||
                            flags and 2 != 0 && Dsu.mac(i).contentEquals(bytes.copyOfRange(22, 28))
                    ) {
                        s.clients[from] = now
                    }
                }
            }
        }
        return emptyList()
    }

    fun expire(now: Long): List<Reply> = buildList {
        for ((i, s) in slots.withIndex()) {
            s.clients.entries.removeAll { now - it.value > 6000 }
            val last = s.lastFrame ?: continue
            if (now - last >= 650) {
                val neutral = Dsu.pad(Pad(i, s.sequence + 1))
                for (client in s.clients.keys) add(Reply(2, client, neutral))
                s.lastFrame = null
                s.owner = null
            }
        }
    }
}
