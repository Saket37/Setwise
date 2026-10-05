package dev.saketanand.setwise.timer

import android.media.AudioManager
import android.os.VibratorManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RestAlertTest {

    private val context = RuntimeEnvironment.getApplication()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val alert = RestAlert(context)

    @Test
    fun `it buzzes, as Settings say`() {
        alert.play(sound = true, vibrate = true)
        assertTrue(shadowOf(vibrator).isVibrating)
    }

    @Test
    fun `no buzz when Settings turn vibration off`() {
        alert.play(sound = true, vibrate = false)
        assertFalse(shadowOf(vibrator).isVibrating)
    }

    @Test
    fun `the ringer wins, silent is nothing and vibrate is a buzz`() {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        alert.play()
        assertFalse(shadowOf(vibrator).isVibrating)

        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        alert.play()
        assertTrue(shadowOf(vibrator).isVibrating)
    }
}
