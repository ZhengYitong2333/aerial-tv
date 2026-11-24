package app.orchard.tv.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeThemeTest {
    private val valid = """{"title":"Aurora","accent":"#497AA6","wallpaper":"grad:aurora","columns":5}"""

    @Test fun validatesThemeBeforeStaging() {
        val theme = HomeTheme.parse(valid)
        assertEquals(theme, HomeTheme.parse(theme.json().toString()))
        for (invalid in listOf(valid.replace("grad:aurora", "https://invalid.example/asset"),
            valid.replace("#497AA6", "red"), valid.replace(":5}", ":5.5}"),
            valid.replace(":5}", ":8}"), valid.replace("Aurora", "A".repeat(65)))) {
            assertTrue(runCatching { HomeTheme.parse(invalid) }.isFailure)
        }
    }
}
