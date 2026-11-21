package es.thormando.unificado

import es.thormando.unificado.domain.DolphinConfig
import org.junit.Assert.*
import org.junit.Test

class DolphinConfigTest {
    @Test fun serverFileContainsOnlyTheThorEndpoint() {
        assertEquals(listOf("[Server]", "Enabled = True", "Entries = Thor:127.0.0.1:26760;", ""),
            DolphinConfig.SERVER_FILE.lines())
    }
    private val profiles = (0..1).map { "[Profile]\nDevice = DSUClient/$it/Thor\nButtons/A = Cross" }

    @Test fun allPlayerCombinationsAndTransitions() {
        val populated = (1..4).joinToString("\n") {
            "[Wiimote$it]\nDevice = Original$it\nSource = 2\nsource = 1"
        } + "\n[BalanceBoard]\nSource = 2\n"
        for (input in listOf("", populated, "\uFEFF" + populated.replace("\n", "\r\n"))) {
            for (first in 1..3) for (next in 1..3) {
                val initial = DolphinConfig.controllers(input, profiles, first)
                val out = DolphinConfig.controllers(initial, profiles, next)
                assertEquals(out, DolphinConfig.controllers(out, profiles, next))
                for (slot in 0..3) {
                    val body = out.substringAfter("[Wiimote${slot + 1}]").substringBefore("[").lines()
                    val active = next and (1 shl slot) != 0
                    assertEquals(listOf(if (active) "Source = 1" else "Source = 0"),
                        body.filter { it.substringBefore('=').trim().equals("source", true) })
                    if (active) assertTrue("Device = DSUClient/$slot/Thor" in body)
                }
                if (input.isNotEmpty()) assertTrue(out.replace("\r", "").contains("[BalanceBoard]\nSource = 2"))
            }
        }
    }

    @Test fun refusesEmptyOrInvalidPlayerSelections() {
        for (mask in listOf(0, -1, 4, 7)) {
            assertTrue(runCatching { DolphinConfig.controllers("", profiles, mask) }.isFailure)
        }
    }
}
