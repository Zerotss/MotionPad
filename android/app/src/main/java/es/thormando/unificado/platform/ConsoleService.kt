package es.thormando.unificado.platform

import android.app.*
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import es.thormando.unificado.net.Receiver
import es.thormando.unificado.net.ReceiverStatus
import es.thormando.motionpad.MainActivity

class ConsoleService : Service() {
    companion object {
        private val stopped = ReceiverStatus("Receiver stopped")
        @Volatile private var current: ConsoleService? = null
        val state: ReceiverStatus get() = current?.latest ?: stopped
    }

    @Volatile private var latest = ReceiverStatus("Opening network ports…")
    private var receiver: Receiver? = null
    private var lock: WifiManager.MulticastLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (receiver != null) return START_NOT_STICKY
        val channel = "recepcion"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    channel,
                    "Dolphin receiver",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        val reopen =
            Intent(this, MainActivity::class.java)
                .putExtra("console", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val action =
            PendingIntent.getActivity(
                this,
                0,
                reopen,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        startForeground(
            1,
            Notification.Builder(this, channel)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("MotionPad")
                .setContentText("Dolphin receiver is running")
                .setContentIntent(action)
                .setOngoing(true)
                .build(),
        )
        lock =
            applicationContext
                .getSystemService(WifiManager::class.java)
                ?.createMulticastLock("Thor:receiver")
                ?.apply { acquire() }
        current = this
        receiver = Receiver(Settings(this).consoleCode) { latest = it }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        current = null
        receiver?.close()
        receiver = null
        lock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
