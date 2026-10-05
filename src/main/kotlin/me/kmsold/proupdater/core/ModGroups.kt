package me.kmsold.proupdater.core

/** A loaded mod as far as grouping cares: its id and display name. */
data class ModInfo(val id: String, val name: String)

/** The settings files of one mod in a backup; [key] is the mod id, or `file:<name>` when no mod matched. */
data class ModGroup(val key: String, val name: String, val paths: List<String>)

/**
 * Sorts settings files by the mod they belong to. Mods name their files and folders after
 * themselves, loosely: `config/NoFrills/`, `config/sodium-options.json`, `config/iq/` for IQ Addons,
 * `config/cubes-without-borders.json` for the mod `cwb`. So a file belongs to the mod whose id or
 * name, letters and digits only, equals its first name in `config/`; failing that, to the longest
 * mod id it starts with; failing that, to the only mod whose id or name starts with it. What is left
 * keeps its own name.
 */
object ModGroups {

    const val MINECRAFT = "minecraft"

    fun group(paths: Collection<String>, mods: List<ModInfo>): List<ModGroup> {
        val byKey = linkedMapOf<String, MutableList<String>>()
        val names = mutableMapOf<String, String>()
        for (path in paths.toSortedSet()) {
            val (key, name) = owner(path, mods)
            byKey.getOrPut(key) { mutableListOf() } += path
            names[key] = name
        }
        return byKey.map { (key, files) -> ModGroup(key, names.getValue(key), files) }
            .sortedWith(compareBy<ModGroup>({ it.key != MINECRAFT }, { it.name.lowercase() }))
    }

    /** The group key and display name for [path]. */
    fun owner(path: String, mods: List<ModInfo>): Pair<String, String> {
        if (path == GamePaths.OPTIONS) return MINECRAFT to "Minecraft (options.txt)"
        if (!path.startsWith("config/")) return "file:$path" to path
        val inConfig = path.removePrefix("config/")
        val first = inConfig.substringBefore('/')
        val stem = if ('/' in inConfig) first else first.substringBefore('.')
        val wanted = normalize(stem)
        if (wanted.isEmpty()) return "file:$stem" to stem

        val match = mods.firstOrNull { normalize(it.id) == wanted || normalize(it.name) == wanted }
            ?: mods.filter { normalize(it.id).let { id -> id.length >= 3 && wanted.startsWith(id) } }
                .maxByOrNull { normalize(it.id).length }
            ?: mods.filter { normalize(it.id).startsWith(wanted) || normalize(it.name).startsWith(wanted) }
                .singleOrNull()
        return if (match != null) match.id to match.name else "file:$stem" to stem
    }

    private fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() }
}
