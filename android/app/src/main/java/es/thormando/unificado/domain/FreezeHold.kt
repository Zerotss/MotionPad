package es.thormando.unificado.domain

/** A single pointer can toggle Freeze only once per uninterrupted hold. */
class FreezeHold {
    companion object {
        const val DURATION_MS = 1000L
    }

    private var pointer: Int? = null
    private var began = 0L

    fun begin(id: Int, now: Long): Boolean {
        if (pointer != null) return false
        pointer = id
        began = now
        return true
    }

    fun cancel() {
        pointer = null
    }

    fun end(id: Int) {
        if (pointer == id) cancel()
    }

    fun move(id: Int, inside: Boolean) {
        if (!inside) end(id)
    }

    fun consume(now: Long): Boolean {
        if (pointer == null || now - began < DURATION_MS) return false
        cancel()
        return true
    }
}
