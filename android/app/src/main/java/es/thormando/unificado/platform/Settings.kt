package es.thormando.unificado.platform

import android.content.Context
import java.security.SecureRandom

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("thor15", Context.MODE_PRIVATE)
    var preferredRole: String?
        get() = prefs.getString("preferredRole", null)
        set(value) {
            require(value == null || value == "play" || value == "console")
            prefs.edit().putString("preferredRole", value).apply()
        }

    val consoleCode: String
        get() {
            val code = prefs.getString("console", null)
            if (code != null) return code
            return (100000 + SecureRandom().nextInt(900000)).toString().also {
                prefs.edit().putString("console", it).apply()
            }
        }

    val phoneCode: String?
        get() = prefs.getString("phone", null)

    val slot
        get() = prefs.getInt("slot", 0)

    var frozen: Boolean
        get() = prefs.getBoolean("frozen", false)
        set(v) {
            prefs.edit().putBoolean("frozen", v).apply()
        }

    var requestedPin: Boolean
        get() = prefs.getBoolean("requestedPin", false)
        set(value) {
            prefs.edit().putBoolean("requestedPin", value).apply()
        }

    init {
        if (!prefs.contains("requestedPin")) requestedPin = frozen
    }

    fun pair(code: String, slot: Int) {
        prefs.edit().putString("phone", code).putInt("slot", slot).apply()
    }

    fun forget() {
        prefs.edit().remove("phone").remove("slot").remove("frozen").apply()
    }
}
