package com.screen.remote.android.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementTextsTest {
    @Test
    fun `all management text pairs are complete and have matching format arguments`() {
        val textPairs =
            ManagementTexts::class.java.declaredClasses.flatMap { category ->
                val instance = category.getField("INSTANCE").get(null)
                category.declaredMethods
                    .filter { method ->
                        method.parameterCount == 0 && method.returnType == TextPair::class.java
                    }.map { method -> method.invoke(instance) as TextPair }
            }

        assertTrue("Management text catalog should not be empty", textPairs.isNotEmpty())
        textPairs.forEach { pair ->
            assertTrue("Chinese management text must not be blank", pair.chinese.isNotBlank())
            assertTrue("English management text must not be blank", pair.english.isNotBlank())
            assertEquals(
                "Format arguments must match for: ${pair.chinese} / ${pair.english}",
                formatArgumentCount(pair.chinese),
                formatArgumentCount(pair.english),
            )
        }
    }

    private fun formatArgumentCount(text: String): Int =
        FORMAT_ARGUMENT.findAll(text).count()

    private companion object {
        val FORMAT_ARGUMENT = Regex("(?<!%)%(?:\\d+\\$)?(?:[-#+ 0,(<]*)?(?:\\d+)?(?:\\.\\d+)?[bBhHsScCdoxXeEfgGaAtT]")
    }
}
