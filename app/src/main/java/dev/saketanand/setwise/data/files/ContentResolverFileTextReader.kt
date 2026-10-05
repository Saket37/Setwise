package dev.saketanand.setwise.data.files

import android.content.ContentResolver
import androidx.core.net.toUri
import dev.saketanand.setwise.domain.ai.FileTextReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [FileTextReader] for content:// and file:// links (a chosen or shared CSV export), off the main thread. */
class ContentResolverFileTextReader(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher,
) : FileTextReader {

    override suspend fun read(uri: String, maxChars: Int): String = withContext(ioDispatcher) {
        val stream = contentResolver.openInputStream(uri.toUri()) ?: error("Couldn't open $uri")
        stream.bufferedReader().use { reader ->
            val buffer = CharArray(maxChars)
            var length = 0
            while (length < maxChars) {
                val read = reader.read(buffer, length, maxChars - length)
                if (read < 0) break
                length += read
            }
            String(buffer, 0, length)
        }
    }
}
