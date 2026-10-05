package dev.saketanand.setwise.ui.navigation

import android.content.Intent
import android.net.Uri
import dev.saketanand.setwise.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppLinksTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun send(type: String, text: String? = null, stream: Uri? = null) = Intent(Intent.ACTION_SEND).apply {
        setType(type)
        text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        stream?.let { putExtra(Intent.EXTRA_STREAM, it) }
    }

    @Test
    fun `a workout link opens MainActivity in place and reads back`() {
        val intent = AppLinks.intent(context, AppLink.Workout(42))

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertEquals(AppLink.Workout(42), AppLinks.from(intent))
    }

    @Test
    fun `shared text opens the import screen with it`() {
        assertEquals(AppLink.SharedText("Bench 60x8"), AppLinks.from(AppLinks.intent(context, AppLink.SharedText("Bench 60x8"))))
        assertEquals(AppLink.SharedText("Squat 100x5"), AppLinks.from(send("text/plain", text = "Squat 100x5")))
    }

    @Test
    fun `very long shared text is cut`() {
        val link = AppLinks.from(send("text/plain", text = "x".repeat(150_000))) as AppLink.SharedText
        assertEquals(100_000, link.text.length)
    }

    @Test
    fun `a shared file comes as its link, text first when there's both`() {
        val csv = Uri.parse("content://downloads/export.csv")
        assertEquals(AppLink.SharedFile(csv.toString()), AppLinks.from(send("text/csv", stream = csv)))
        assertEquals(AppLink.SharedText("notes"), AppLinks.from(send("text/csv", text = "notes", stream = csv)))
    }

    @Test
    fun `shared screenshots come as their links`() {
        val shots = arrayListOf(Uri.parse("content://media/1"), Uri.parse("content://media/2"))
        val many = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png").putParcelableArrayListExtra(Intent.EXTRA_STREAM, shots)
        assertEquals(AppLink.SharedImages(listOf("content://media/1", "content://media/2")), AppLinks.from(many))

        val one = send("image/jpeg", stream = Uri.parse("content://media/3"))
        assertEquals(AppLink.SharedImages(listOf("content://media/3")), AppLinks.from(one))
    }

    @Test
    fun `intents with nothing to open are ignored`() {
        assertNull(AppLinks.from(null))
        assertNull(AppLinks.from(Intent(Intent.ACTION_MAIN))) // the launcher
        assertNull(AppLinks.from(Intent(Intent.ACTION_VIEW).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "hi"))) // another action
        assertNull(AppLinks.from(send("text/plain"))) // no extras
        assertNull(AppLinks.from(send("text/plain", text = "   "))) // empty text
        assertNull(AppLinks.from(send("image/png"))) // an image share without the image
    }
}
