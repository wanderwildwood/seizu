package com.wanderwildwood.seizu.sky

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class PreferencesTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun aRealPlaceIsSaved() {
        val prefs = Preferences(context)
        prefs.latitude = 42.36
        prefs.longitude = -71.06
        assertTrue(prefs.placeSaved)
        assertEquals(42.36, prefs.latitude, 1e-12)
    }

    @Test
    fun aPlaceThatIsNotANumberIsAskedAgain() {
        val prefs = Preferences(context)
        prefs.latitude = Double.NaN
        prefs.longitude = -71.06
        assertFalse(prefs.placeSaved)
        assertEquals(51.4778, prefs.latitude, 1e-12)
    }

    @Test
    fun nothingStoredIsNotSaved() {
        assertFalse(Preferences(context).placeSaved)
    }
}
