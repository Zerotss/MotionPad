package es.thormando.unificado.domain

import java.util.ArrayDeque

data class Vector(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0)

data class Pad(
    val slot: Int,
    val sequence: Int,
    val keys: Set<Key> = emptySet(),
    val aim: Pair<Int, Int> = 128 to 128,
    val micros: Long = 0,
    val accel: Vector = Vector(),
    val gyro: Vector = Vector(),
)

enum class Key(val label: String, val offset: Int, val mask: Int, val analog: Int = -1) {
    UP("↑", 36, 16, 47),
    RIGHT("→", 36, 32, 46),
    DOWN("↓", 36, 64, 45),
    LEFT("←", 36, 128, 44),
    MINUS("−", 36, 1),
    PLUS("+", 36, 8),
    HOME("HOME", 38, 1),
    A("A", 37, 64, 49),
    B("B", 37, 16, 51),
    ONE("1", 37, 128, 48),
    TWO("2", 37, 32, 50),
    CENTER("◎", 36, 4),
}

/** Preserve short presses and release edges. Times are monotonic milliseconds. */
class Buttons {
    private class Button {
        val edges = ArrayDeque<Boolean>()
        var physical = false
        var pressed = false
        var until = 0L
    }

    private val buttons = Key.entries.associateWith { Button() }

    fun touch(key: Key, down: Boolean) {
        val b = buttons.getValue(key)
        if (b.physical == down) return
        b.physical = down
        b.edges.addLast(down)
    }

    fun sample(now: Long): Set<Key> =
        buttons
            .filter { (key, b) ->
                if (b.edges.isNotEmpty() && now >= b.until) {
                    b.pressed = b.edges.removeFirst()
                    b.until =
                        now +
                            when {
                                !b.pressed -> 18
                                key == Key.CENTER -> 100
                                else -> 36
                            }
                }
                b.pressed
            }
            .keys

    fun clear() =
        buttons.values.forEach {
            it.edges.clear()
            it.physical = false
            it.pressed = false
            it.until = 0L
        }
}
