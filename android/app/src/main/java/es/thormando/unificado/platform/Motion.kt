package es.thormando.unificado.platform

import android.content.Context
import android.hardware.*
import android.os.SystemClock
import es.thormando.unificado.domain.*

class Motion(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val buttons = Buttons()
    private var aim = Aim()
    private var accel = Vector()
    private var gyro = Vector()
    private var stamp = 0L
    private var sequence = 0
    @Volatile
    var accelerationSeen = false
        private set

    @Volatile
    var gyroSeen = false
        private set

    fun start() {
        for (type in
            listOf(
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_GYROSCOPE,
                Sensor.TYPE_GAME_ROTATION_VECTOR,
            )) {
            manager.getDefaultSensor(type)?.let {
                manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    @Synchronized
    fun stop() {
        manager.unregisterListener(this)
        buttons.clear()
        aim = Aim()
        accel = Vector()
        gyro = Vector()
        stamp = 0
        accelerationSeen = false
        gyroSeen = false
    }

    @Synchronized
    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        val vector = Vector(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accel = vector
                stamp = event.timestamp / 1000
                accelerationSeen = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyro = vector
                aim.gyro(vector, event.timestamp)
                gyroSeen = true
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                val q = FloatArray(4)
                SensorManager.getQuaternionFromVector(q, v)
                aim.rotation(q[1].toDouble(), q[2].toDouble(), q[3].toDouble(), q[0].toDouble())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Synchronized
    fun key(key: Key, down: Boolean) {
        if (down && key == Key.CENTER) aim.center()
        buttons.touch(key, down)
    }

    @Synchronized fun release() = buttons.clear()

    @Synchronized
    fun frame(slot: Int) =
        Dsu.pad(
            Pad(
                slot,
                ++sequence,
                buttons.sample(SystemClock.elapsedRealtime()),
                aim.axes(),
                stamp,
                accel,
                gyro,
            )
        )
}
