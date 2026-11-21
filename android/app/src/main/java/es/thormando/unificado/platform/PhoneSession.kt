package es.thormando.unificado.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import es.thormando.unificado.domain.Key
import es.thormando.unificado.net.Handset

/** Owned by the foreground Activity. A status callback from a closed link is discarded. */
class PhoneSession(context: Context) {
    private val motion = Motion(context)
    private val main = Handler(Looper.getMainLooper())
    private var link: Handset? = null
    private var generation = 0

    fun start(code: String, slot: Int, status: (String) -> Unit) {
        if (link != null) return
        val current = ++generation
        motion.start()
        link =
            Handset(code, slot, { motion.frame(slot) }) { message ->
                main.post { if (generation == current) status(message) }
            }
    }

    fun stop() {
        generation++
        link?.close()
        link = null
        motion.stop()
    }

    fun key(key: Key, down: Boolean) {
        motion.key(key, down)
        link?.inputChanged()
    }

    fun release() {
        motion.release()
        link?.inputChanged()
    }

    val accelerationSeen
        get() = motion.accelerationSeen

    val gyroSeen
        get() = motion.gyroSeen
}
