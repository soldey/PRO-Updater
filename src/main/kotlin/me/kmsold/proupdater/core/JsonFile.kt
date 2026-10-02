package me.kmsold.proupdater.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * A mod's JSON config, edited as a tree and written back the way the mods themselves write it:
 * Gson pretty printing with a two-space indent. Whether `<>&='` are written as unicode escapes
 * is copied from the original file, so untouched values come back byte for byte.
 */
class JsonFile private constructor(val root: JsonElement, private val escapeHtml: Boolean, private val trailingNewline: Boolean) {

    var changed = false
        private set

    fun toText(): String {
        val builder = GsonBuilder().setPrettyPrinting()
        if (!escapeHtml) builder.disableHtmlEscaping()
        return builder.create().toJson(root) + if (trailingNewline) "\n" else ""
    }

    /** The object at [path], created on the way when [create] is set; null when something else is in the way. */
    fun objectAt(vararg path: String, create: Boolean = false): JsonObject? {
        var node = root as? JsonObject ?: return null
        for (key in path) {
            val next = node.get(key)
            node = when {
                next is JsonObject -> next
                next == null && create -> JsonObject().also {
                    node.add(key, it)
                    changed = true
                }
                else -> return null
            }
        }
        return node
    }

    /** Sets `path.last()` inside the object at the rest of [path]. Returns false when the parents are not objects. */
    fun set(path: List<String>, value: JsonElement, create: Boolean = true): Boolean {
        val parent = objectAt(*path.dropLast(1).toTypedArray(), create = create) ?: return false
        if (parent.get(path.last()) != value) {
            parent.add(path.last(), value)
            changed = true
        }
        return true
    }

    fun setBoolean(path: List<String>, value: Boolean, create: Boolean = true) = set(path, JsonPrimitive(value), create)

    /** For callers that changed the tree themselves. */
    fun markChanged() {
        changed = true
    }

    companion object {
        private val HTML_ESCAPES = listOf("\\u003c", "\\u003e", "\\u0026", "\\u003d", "\\u0027")

        fun parse(text: String): JsonFile? {
            val root = runCatching { JsonParser.parseString(text) }.getOrNull() ?: return null
            if (!root.isJsonObject && !root.isJsonArray) return null
            return JsonFile(root, HTML_ESCAPES.any { text.contains(it, ignoreCase = true) }, text.endsWith("\n"))
        }

        fun array(vararg values: JsonElement) = JsonArray().also { a -> values.forEach(a::add) }
    }
}

/** Reads [path] as JSON, lets [edit] change it and writes it back only when something changed. */
fun FileChanges.editJson(path: String, edit: (JsonFile) -> Unit): EditResult {
    val text = readText(path) ?: return EditResult.MISSING
    val json = JsonFile.parse(text) ?: return EditResult.UNREADABLE
    edit(json)
    if (json.changed) writeText(path, json.toText())
    return if (json.changed) EditResult.CHANGED else EditResult.UNCHANGED
}

/** Reads `options.txt`, lets [edit] change it and writes it back. */
fun FileChanges.editOptions(edit: (OptionsFile) -> Unit): EditResult {
    val text = readText(GamePaths.OPTIONS) ?: return EditResult.MISSING
    val options = OptionsFile.parse(text)
    edit(options)
    val result = options.toText()
    if (result == text) return EditResult.UNCHANGED
    writeText(GamePaths.OPTIONS, result)
    return EditResult.CHANGED
}

enum class EditResult { CHANGED, UNCHANGED, MISSING, UNREADABLE }
