package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/** Host-owned local OCR boundary; callers must keep the recognized text under user review. */
internal fun interface LocalImageOcr {
    fun recognize(context: Context, uri: Uri, onResult: (Result<String>) -> Unit)
}

internal class LocalImageReadException(cause: Throwable) : Exception(cause)

internal object MlKitLocalImageOcr : LocalImageOcr {
    override fun recognize(context: Context, uri: Uri, onResult: (Result<String>) -> Unit) {
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        try {
            val image = try {
                InputImage.fromFilePath(context, uri)
            } catch (error: Exception) {
                throw LocalImageReadException(error)
            }
            recognizer.process(image)
                .addOnSuccessListener { onResult(Result.success(it.text)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
                .addOnCompleteListener { recognizer.close() }
        } catch (error: Exception) {
            recognizer.close()
            throw error
        }
    }
}
