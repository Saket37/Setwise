package dev.saketanand.setwise.llm

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.saketanand.setwise.domain.ai.TextReader
import dev.saketanand.setwise.domain.model.OcrLine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [TextReader] on ML Kit text recognition (Latin script, on-device; the model comes with Google
 * Play services). A recognizer per photo, closed when done; the photo isn't kept.
 */
class MlKitTextReader(private val context: Context) : TextReader {

    override suspend fun read(uri: String): List<OcrLine> {
        val image = InputImage.fromFilePath(context, Uri.parse(uri))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            return text.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box.left, box.top, box.right, box.bottom)
            }
        } finally {
            recognizer.close()
        }
    }
}
