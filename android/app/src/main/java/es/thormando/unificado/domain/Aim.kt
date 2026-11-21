package es.thormando.unificado.domain

import kotlin.math.*

private data class Q(
    val w: Double = 1.0,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
) {
    operator fun times(b: Q) =
        Q(
            w * b.w - x * b.x - y * b.y - z * b.z,
            w * b.x + x * b.w + y * b.z - z * b.y,
            w * b.y - x * b.z + y * b.w + z * b.x,
            w * b.z + x * b.y - y * b.x + z * b.w,
        )

    fun conjugate() = Q(w, -x, -y, -z)

    fun normalized(): Q? {
        val n = sqrt(w * w + x * x + y * y + z * z)
        return if (n.isFinite() && n > 0) Q(w / n, x / n, y / n, z / n) else null
    }
}

class Aim {
    private var pose = Q()
    private var origin = Q()
    private var fused = false
    private var previous = 0L

    fun rotation(x: Double, y: Double, z: Double, w: Double) {
        pose = Q(w, x, y, z).normalized() ?: return
        if (!fused) origin = pose
        fused = true
    }

    fun gyro(r: Vector, nanos: Long) {
        val dt = (nanos - previous) / 1e9
        val first = previous == 0L
        previous = nanos
        val speed = sqrt(r.x * r.x + r.y * r.y + r.z * r.z)
        if (first || fused || dt <= 0 || dt > .15 || !speed.isFinite() || speed < .00001) return
        val half = speed * dt / 2
        val k = sin(half) / speed
        pose = (pose * Q(cos(half), r.x * k, r.y * k, r.z * k)).normalized() ?: pose
    }

    fun center() {
        origin = pose
        previous = 0
    }

    fun axes(): Pair<Int, Int> {
        val (w, x, y, z) = origin.conjugate() * pose
        val right = 2 * (x * y - w * z)
        val forward = 1 - 2 * (x * x + z * z)
        fun axis(a: Double, range: Double) =
            (128 + Math.toDegrees(a) * 127 / range).roundToInt().coerceIn(0, 255)
        return axis(atan2(right, forward), 30.0) to
            axis(atan2(2 * (y * z + w * x), hypot(right, forward)), 22.0)
    }
}
