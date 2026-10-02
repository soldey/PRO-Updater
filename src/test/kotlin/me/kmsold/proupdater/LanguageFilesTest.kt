package me.kmsold.proupdater

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A key missing from a translation would only show up in game, as the raw key on the button. */
class LanguageFilesTest {

    private fun keys(code: String): Set<String> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/assets/proupdater/lang/$code.json")) {
            "missing language file $code.json"
        }
        return stream.bufferedReader().use { Gson().fromJson(it, JsonObject::class.java) }.keySet()
    }

    @Test
    fun `russian has exactly the english keys`() {
        assertEquals(keys("en_us"), keys("ru_ru"))
    }
}
