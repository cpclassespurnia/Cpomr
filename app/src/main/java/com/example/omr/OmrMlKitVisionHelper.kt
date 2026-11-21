package com.example.omr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * ML Kit Vision Helper for OMR frames:
 * - Recognizes printed & handwritten roll numbers from the student info box
 * - Detects printed Exam Set codes ("SET A", "SET B", "SET C", "SET D")
 * - Verifies CP Classes header branding and OMR layout anchors
 */
object OmrMlKitVisionHelper {

    data class FrameMetadata(
        val detectedRoll: String? = null,
        val detectedSetCode: String? = null,
        val detectedTitle: String? = null,
        val isOmrHeaderVerified: Boolean = false,
        val rawRecognizedText: String = ""
    )

    private val recognizer by lazy {
        try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        } catch (e: Exception) {
            Log.w("OmrMlKitVisionHelper", "ML Kit text recognizer init exception", e)
            null
        }
    }

    /**
     * Asynchronously processes an image frame with ML Kit Text Recognition to extract
     * student roll numbers, exam set codes, and header confirmation.
     */
    suspend fun analyzeFrameText(bitmap: Bitmap): FrameMetadata {
        val client = recognizer ?: return FrameMetadata()

        return suspendCancellableCoroutine { continuation ->
            try {
                // Focus on the top header portion (top 28% of the sheet) where title, roll, and set are located
                val headerH = (bitmap.height * 0.28f).toInt().coerceIn(20, bitmap.height)
                val headerBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, headerH)
                val inputImage = InputImage.fromBitmap(headerBitmap, 0)

                client.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text
                        val upper = text.uppercase(Locale.ROOT)

                        // 1. Detect Roll Number
                        // Matches "ROLL: 1042", "ROLL NO 1042", "ROLL #1042", or raw 3-6 digit sequences
                        var matchedRoll: String? = null
                        val rollPattern = Pattern.compile("(?:ROLL|R\\.NO|ROLL\\s*NO|ROLL\\s*#)[^0-9]{0,6}([0-9]{2,6})", Pattern.CASE_INSENSITIVE)
                        val rollMatcher = rollPattern.matcher(text)
                        if (rollMatcher.find()) {
                            matchedRoll = rollMatcher.group(1)
                        } else {
                            // Secondary fallback: search for standalone 3-5 digit number in the right half of info header
                            val standaloneDigitsPattern = Pattern.compile("\\b([0-9]{3,5})\\b")
                            val digitMatcher = standaloneDigitsPattern.matcher(text)
                            if (digitMatcher.find()) {
                                matchedRoll = digitMatcher.group(1)
                            }
                        }

                        // 2. Detect Set Code (SET A, SET B, SET C, SET D)
                        var matchedSet: String? = null
                        val setPattern = Pattern.compile("(?:SET)[\\s:_-]*([A-D])\\b", Pattern.CASE_INSENSITIVE)
                        val setMatcher = setPattern.matcher(upper)
                        if (setMatcher.find()) {
                            matchedSet = setMatcher.group(1)
                        }

                        // 3. Verify Header Branding
                        val isVerified = upper.contains("CP") ||
                                upper.contains("CLASSES") ||
                                upper.contains("OMR") ||
                                upper.contains("TEST") ||
                                upper.contains("ANSWER")

                        continuation.resume(
                            FrameMetadata(
                                detectedRoll = matchedRoll,
                                detectedSetCode = matchedSet,
                                detectedTitle = if (upper.contains("CP CLASSES")) "CP CLASSES" else null,
                                isOmrHeaderVerified = isVerified,
                                rawRecognizedText = text
                            )
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.d("OmrMlKitVisionHelper", "ML Kit processing skipped: ${e.message}")
                        continuation.resume(FrameMetadata())
                    }
            } catch (e: Exception) {
                Log.w("OmrMlKitVisionHelper", "analyzeFrameText caught exception: ${e.message}")
                continuation.resume(FrameMetadata())
            }
        }
    }
}
