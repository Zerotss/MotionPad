package es.thormando.unificado.bridge

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.view.WindowManager
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.ViewManager
import es.thormando.unificado.platform.*
import es.thormando.unificado.domain.Key

class MotionPadPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> = listOf(MotionPad(context))
    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}

class MotionPad(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context), LifecycleEventListener {
    private val settings = Settings(context)
    private val phone = PhoneSession(context)
    private var mode = "home"
    private var resumed = false
    private var status = ""
    private var setupMask = 0
    private var linking: Promise? = null
    private val linkPrefs = context.getSharedPreferences("dolphin-link", 0)
    init {
        context.addLifecycleEventListener(this)
        context.addActivityEventListener(object : BaseActivityEventListener() {
            override fun onNewIntent(intent: Intent) {
                if(intent.getBooleanExtra("console",false)) restoreConsole()
            }
            override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
                if (requestCode == 82 || requestCode == 83) {
                    val pending = linking ?: return
                    val tree = data?.data
                    if (resultCode != Activity.RESULT_OK || tree == null) {
                        linking = null
                        pending.resolve(false)
                        return
                    }
                    configureFolder(tree, pending, picked = true, install = requestCode == 82)
                    return
                }
                if (requestCode !in 80..81 || resultCode != Activity.RESULT_OK) return
                val uri = data?.data ?: return
                val result = runCatching {
                    context.assets.open("ThorCentro-J${requestCode-79}.ini").use { input ->
                        requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use { input.copyTo(it) }
                    }
                }
                android.widget.Toast.makeText(context, if(result.isSuccess) "Profile saved" else "Could not save the profile", 0).show()
            }
        })
    }
    override fun getName() = "MotionPad"
    private fun ui(action: () -> Unit) { UiThreadUtil.runOnUiThread { action() } }
    private fun connect() {
        if (mode=="play" && resumed) settings.phoneCode?.let { phone.start(it,settings.slot) { message -> status=message } }
    }
    private fun restoreConsole() {
        if (mode != "console") enterMode("console")
    }
    override fun onHostResume() {
        resumed = true
        val launch = currentActivity?.intent
        val role = settings.preferredRole
        if (launch?.getBooleanExtra("console", false) == true) {
            launch.removeExtra("console")
            restoreConsole()
        } else if (mode == "home" && role != null) {
            enterMode(role)
        } else {
            connect()
        }
    }
    override fun onHostPause() { resumed=false; phone.stop() }
    override fun onHostDestroy() { phone.stop(); mode="home" }
    private fun pinned() = context.getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    private fun protectFreeze() {
        val activity = currentActivity ?: return
        if (!pinned()) {
            settings.requestedPin = runCatching { activity.startLockTask() }.isSuccess
        }
        val window = activity.window
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.apply {
                systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(android.view.WindowInsets.Type.navigationBars())
            }
        } else {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }
    private fun showNavigation() {
        val window = currentActivity?.window ?: return
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.show(android.view.WindowInsets.Type.navigationBars())
        } else {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and
                (android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY).inv()
        }
    }
    private fun releaseScreenPin() {
        if (settings.requestedPin && context.getSystemService(ActivityManager::class.java).lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED) {
            requireNotNull(currentActivity).stopLockTask()
        }
        settings.requestedPin = false
    }
    private fun state() = Arguments.createMap().apply {
        val receiver = ConsoleService.state
        putInt("connectedMask", receiver.connectedMask)
        putBoolean("dolphinFolderSaved", linkPrefs.contains("tree"))
        putBoolean("dolphinConfigured", linkPrefs.getBoolean("configured", false))
        putString("mode",mode); putString("code",settings.consoleCode); putBoolean("paired",settings.phoneCode!=null)
        putInt("slot",settings.slot); putBoolean("frozen",settings.frozen); putBoolean("pinned",pinned())
        putString("status",if(mode=="console") receiver.message else status)
        putBoolean("accel",phone.accelerationSeen); putBoolean("gyro",phone.gyroSeen)
    }
    @ReactMethod fun snapshot(p: Promise) = ui { p.resolve(state()) }
    private fun enterMode(next: String) {
        if (mode == "play" && next != "play") {
            showNavigation()
            releaseScreenPin()
        }
        phone.stop()
        val service = Intent(context, ConsoleService::class.java)
        if (mode == "console" && next != "console") context.stopService(service)
        val destination = if (next == "play" && settings.phoneCode == null) "pair" else next
        if (destination == "console") context.startForegroundService(service)
        mode = destination
        status = "Looking for your Thor on Wi-Fi…"
        currentActivity?.window?.apply {
            val bars = WindowCompat.getInsetsController(this, decorView)
            if (mode == "play") bars.hide(WindowInsetsCompat.Type.statusBars())
            else bars.show(WindowInsetsCompat.Type.statusBars())
            if (mode == "play" || mode == "console") addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (mode == "play" && settings.frozen) protectFreeze()
        connect()
    }
    @ReactMethod fun navigate(next: String, p: Promise) = ui {
        try {
            require(next in setOf("play", "console", "home", "pair"))
            enterMode(next)
            when (next) {
                "play", "console" -> settings.preferredRole = next
                "home" -> settings.preferredRole = null
            }
            p.resolve(state())
        } catch(e: Exception) { p.reject("session", e) }
    }
    @ReactMethod fun pair(code: String,slot: Int,p: Promise) = ui {
        if(code.matches(Regex("[0-9]{6}")) && slot in 0..1) {settings.pair(code,slot); p.resolve(true)}
        else p.reject("code","Enter six digits")
    }
    @ReactMethod fun switchPlayer(p: Promise) = ui {
        phone.stop(); settings.phoneCode?.let { settings.pair(it,1-settings.slot) }; connect(); p.resolve(state())
    }
    @ReactMethod fun forget(p: Promise) = ui { settings.forget(); enterMode("pair"); p.resolve(state()) }
    @ReactMethod fun key(name:String,down:Boolean) = ui {
        if(mode=="play" && resumed && !settings.frozen) {
            val k=Key.entries.firstOrNull { it.name==name } ?: return@ui
            phone.key(k,down)
            if(down && k!=Key.CENTER) currentActivity?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    @ReactMethod fun freeze(enabled: Boolean, p: Promise) = ui {
        try {
            check(mode == "play" && currentActivity != null)
            if (settings.frozen != enabled) {
                phone.release()
                if (enabled) {
                    protectFreeze()
                } else {
                    releaseScreenPin()
                    showNavigation()
                }
                settings.frozen = enabled
            }
            p.resolve(state())
        } catch (error: Exception) {
            p.reject("E_FREEZE", "Could not change Freeze. Please try again.", error)
        }
    }
    @ReactMethod fun leave() = ui {
        if (mode == "play") settings.frozen = false
        enterMode("home")
        currentActivity?.moveTaskToBack(true)
    }
    private val folderAccess = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    private fun configureFolder(tree: Uri, pending: Promise, picked: Boolean, install: Boolean) {
        kotlin.concurrent.thread(name = "MotionPad-Dolphin-setup") {
            val previous = linkPrefs.getString("tree", null)
            val resolver = context.contentResolver
            var acquired = false
            val result = runCatching {
                val link = DolphinLink(context, tree)
                val folder = link.configFolder()
                if (picked) {
                    resolver.takePersistableUriPermission(tree, folderAccess)
                    acquired = true
                }
                if (install) {
                    check(setupMask in 1..3 && ConsoleService.state.connectedMask == setupMask) {
                        "The connected players changed. Check the receiver and run setup again."
                    }
                    link.install(folder, setupMask)
                }
                if (picked) {
                    linkPrefs.edit().putString("tree", tree.toString())
                        .putBoolean("configured", install || (previous == tree.toString() && linkPrefs.getBoolean("configured", false)))
                        .apply()
                    if (previous != null && previous != tree.toString()) runCatching {
                        resolver.releasePersistableUriPermission(Uri.parse(previous), folderAccess)
                    }
                } else if (install) {
                    linkPrefs.edit().putBoolean("configured", true).apply()
                }
            }
            if (result.isFailure && acquired && previous != tree.toString()) runCatching {
                resolver.releasePersistableUriPermission(tree, folderAccess)
            }
            ui {
                linking = null
                result.onSuccess { pending.resolve(true) }.onFailure {
                    val message = if (!picked && it is SecurityException)
                        "Folder access expired. Tap the dolphin icon and select Dolphin Emulator → Config again."
                    else it.message ?: "Could not access Dolphin Emulator → Config."
                    pending.reject("dolphin", message, it)
                }
            }
        }
    }

    @ReactMethod fun linkDolphin(changeFolder: Boolean, expectedMask: Int, p: Promise) = ui {
        if (linking != null || mode != "console") {
            p.reject("dolphin", "Open the console screen and finish any pending setup.")
            return@ui
        }
        try {
            if (!changeFolder) require(expectedMask in 1..3 && ConsoleService.state.connectedMask == expectedMask) {
                "Check which phones are connected and run setup again."
            }
            setupMask = expectedMask
            val saved = linkPrefs.getString("tree", null)?.let(Uri::parse)
            linking = p
            if (saved != null && !changeFolder) {
                configureFolder(saved, p, picked = false, install = true)
                return@ui
            }
            val activity = requireNotNull(currentActivity)
            val initial = android.provider.DocumentsContract.buildDocumentUri(DolphinLink.AUTHORITY, "root/Config")
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, initial)
                .addFlags(folderAccess or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            activity.startActivityForResult(picker, if (changeFolder) 83 else 82)
        } catch (e: Exception) {
            linking = null
            p.reject("dolphin", "Could not open the Android folder picker.", e)
        }
    }
    @ReactMethod fun openDolphin(p: Promise) = ui {
        try {
            val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(DolphinLink.PACKAGE)) {
                "Install the official Dolphin Emulator on this device."
            }
            requireNotNull(currentActivity).startActivity(launch)
            p.resolve(true)
        } catch (e: Exception) { p.reject("dolphin", e) }
    }
    @ReactMethod fun export(slot:Int) = ui {
        if (slot in 0..1) runCatching {
            currentActivity?.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"ThorCentro-J${slot+1}.ini"),80+slot)
        }
    }
}
