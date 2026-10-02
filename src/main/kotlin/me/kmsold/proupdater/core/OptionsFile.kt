package me.kmsold.proupdater.core

/**
 * `options.txt` as lines. Minecraft splits each line at the first `:`; every line the mod does not
 * touch, known or not, is kept as it was and in its place.
 */
class OptionsFile private constructor(private val lines: MutableList<String>, private val trailingNewline: Boolean) {

    private fun keyOf(line: String): String? = line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it) }

    private fun indexOf(key: String) = lines.indexOfFirst { keyOf(it) == key }

    val keys: List<String> get() = lines.mapNotNull(::keyOf)

    operator fun get(key: String): String? = indexOf(key).takeIf { it >= 0 }?.let { lines[it].substring(key.length + 1) }

    operator fun contains(key: String) = indexOf(key) >= 0

    /** Replaces the value in place, or appends the key at the end. */
    operator fun set(key: String, value: String) {
        val line = "$key:$value"
        val index = indexOf(key)
        if (index >= 0) lines[index] = line else lines += line
    }

    /** Copies the keys of [pack] that this file lacks, in the pack's order, to the end. */
    fun addMissingFrom(pack: OptionsFile): OptionsFile {
        val own = keys.toHashSet()
        for (key in pack.keys) {
            if (key !in own) {
                set(key, pack[key]!!)
                own += key
            }
        }
        return this
    }

    /** The `resourcePacks` list, empty when missing or unreadable. */
    var resourcePacks: List<String>
        get() = this["resourcePacks"]?.let(ModelJson::resourcePacks) ?: emptyList()
        set(value) {
            this["resourcePacks"] = com.google.gson.Gson().toJson(value)
        }

    fun toText(): String = lines.joinToString("\n") + if (trailingNewline) "\n" else ""

    companion object {
        fun parse(text: String): OptionsFile {
            val normalised = text.replace("\r\n", "\n")
            val trailing = normalised.endsWith("\n")
            val body = if (trailing) normalised.dropLast(1) else normalised
            val lines = if (body.isEmpty()) mutableListOf() else body.split('\n').toMutableList()
            return OptionsFile(lines, trailing || body.isEmpty())
        }
    }
}
