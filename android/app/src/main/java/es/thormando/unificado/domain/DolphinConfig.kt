package es.thormando.unificado.domain

/** Dolphin's complete DSU configuration and the two MotionPad controller slots. */
object DolphinConfig {
    private val header = Regex("""^\s*\[([^]]+)]\s*(?:[#;].*)?$""")
    private fun section(text: String, name: String, change: (List<String>) -> List<String>): String {
        val newline = if ("\r\n" in text) "\r\n" else "\n"
        val bom = if (text.startsWith('\uFEFF')) "\uFEFF" else ""
        val lines = text.removePrefix(bom).replace("\r\n", "\n").trimEnd('\n').split('\n').toMutableList()
        if (lines == listOf("")) lines.clear()
        val starts = lines.indices.filter { header.matchEntire(lines[it])?.groupValues?.get(1).equals(name, true) }
        require(starts.size <= 1) { "Duplicate [$name] sections. Check your Dolphin configuration first." }
        if (starts.isEmpty()) {
            lines.add("[$name]")
            lines.addAll(change(emptyList()))
        } else {
            val first = starts.single() + 1
            val end = (first until lines.size).firstOrNull { header.matches(lines[it]) } ?: lines.size
            val body = lines.subList(first, end)
            val updated = change(body.toList())
            body.clear()
            body.addAll(updated)
        }
        return bom + lines.joinToString(newline) + newline
    }

    const val SERVER_FILE = "[Server]\nEnabled = True\nEntries = Thor:127.0.0.1:26760;\n"

    fun controllers(text: String, profiles: List<String>, connectedMask: Int): String {
        require(profiles.size == 2 && connectedMask in 1..3)
        return (0..3).fold(text) { result, slot ->
            section(result, "Wiimote${slot + 1}") { original ->
                if (connectedMask and (1 shl slot) == 0) {
                    original.filterNot { it.substringBefore('=', "").trim().equals("Source", true) } + "Source = 0"
                } else {
                    val lines = profiles[slot].trim().lines()
                    require(lines.first() == "[Profile]" && "Device = DSUClient/$slot/Thor" in lines)
                    listOf("Source = 1") + lines.drop(1)
                }
            }
        }
    }
}
