package me.kmsold.proupdater.core

/**
 * Minecraft key names (`key.keyboard.u`) next to the GLFW key codes some mods store instead
 * (NoFrills writes `"key": 85` for U). Written out by hand from the GLFW key table, without
 * Minecraft classes, because it is used before the game starts.
 */
object KeyNames {

    const val UNBOUND = "key.keyboard.unknown"

    private val codes: Map<String, Int> = buildMap {
        ('a'..'z').forEach { put(it.toString(), it.uppercaseChar().code) }
        ('0'..'9').forEach { put(it.toString(), it.code) }
        (1..25).forEach { put("f$it", 289 + it) }
        (0..9).forEach { put("keypad.$it", 320 + it) }
        putAll(
            mapOf(
                "space" to 32, "apostrophe" to 39, "comma" to 44, "minus" to 45, "period" to 46, "slash" to 47,
                "semicolon" to 59, "equal" to 61, "left.bracket" to 91, "backslash" to 92, "right.bracket" to 93,
                "grave.accent" to 96, "world.1" to 161, "world.2" to 162,
                "escape" to 256, "enter" to 257, "tab" to 258, "backspace" to 259, "insert" to 260, "delete" to 261,
                "right" to 262, "left" to 263, "down" to 264, "up" to 265, "page.up" to 266, "page.down" to 267,
                "home" to 268, "end" to 269, "caps.lock" to 280, "scroll.lock" to 281, "num.lock" to 282,
                "print.screen" to 283, "pause" to 284,
                "keypad.decimal" to 330, "keypad.divide" to 331, "keypad.multiply" to 332, "keypad.subtract" to 333,
                "keypad.add" to 334, "keypad.enter" to 335, "keypad.equal" to 336,
                "left.shift" to 340, "left.control" to 341, "left.alt" to 342, "left.win" to 343,
                "right.shift" to 344, "right.control" to 345, "right.alt" to 346, "right.win" to 347, "menu" to 348,
            ),
        )
    }.mapKeys { "key.keyboard.${it.key}" }

    private val names: Map<Int, String> = codes.entries.associate { (name, code) -> code to name }

    /** The GLFW code of a keyboard key name, or null for mouse buttons, unbound and unknown names. */
    fun glfwCode(name: String): Int? = codes[name]

    fun nameOf(glfwCode: Int): String? = names[glfwCode]

    fun isKeyboard(name: String) = name.startsWith("key.keyboard.")
}
