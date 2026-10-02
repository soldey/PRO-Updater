package me.kmsold.proupdater

import me.kmsold.proupdater.core.FileChanges
import me.kmsold.proupdater.core.MapSource
import me.kmsold.proupdater.steps.StepContext

/** An in-memory game folder with text files. */
fun memory(vararg files: Pair<String, String>): MapSource =
    MapSource(files.associate { (path, text) -> path to text.toByteArray() }.toMutableMap())

fun context(source: MapSource, vararg mods: String): StepContext = StepContext(FileChanges(source)) { it in mods }

fun StepContext.text(path: String): String? = files.readText(path)
