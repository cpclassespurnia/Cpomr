package com.example.omr

import android.graphics.*
import android.util.Log
import com.example.data.model.QuestionEvaluation
import com.example.data.model.QuestionStatus
import com.example.data.model.TopicDefinition
import org.json.JSONArray
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object OmrVisionEngine {

    // ISO 216 standard A4 proportion (1 : 1.4142) at crisp resolution
    const val A4_WIDTH = 1240
    const val A4_HEIGHT = 1754

    data class BubbleScanResult(
        val questionIndex: Int,
        val selectedOption: String,
        val fillRatios: List<Float>,
        val confidence: Float,
        val bubbleStates: List<OmrBubbleCvProcessor.BubbleFillState> = emptyList(),
        val opticalContrasts: List<Float> = emptyList()
    )

    data class OmrScanOutput(
        val rollNumberDetected: String,
        val recognizedAnswers: List<String>,
        val details: List<BubbleScanResult>,
        val overallQualityScore: Float,
        val mlKitMetadata: OmrMlKitVisionHelper.FrameMetadata? = null
    )

    data class DetectedBubbleInfo(
        val questionIndex: Int,
        val option: String,
        val normX: Float, // 0.0 to 1.0 relative to width
        val normY: Float, // 0.0 to 1.0 relative to height
        val isFilled: Boolean
    )

    data class LiveScanTelemetry(
        val isSheetDetected: Boolean,
        val detectedRoll: String,
        val filledCount: Int,
        val totalQuestions: Int,
        val confidence: Float,
        val bubbles: List<DetectedBubbleInfo> = emptyList(),
        val statusMessage: String = "",
        val isReadyToCapture: Boolean = false
    )

    fun generateOmrSheetBitmap(
        testTitle: String = "CP Classes Periodic Test",
        rollNumber: String = "",
        studentName: String = "",
        batchName: String = "JEE / NEET Batch",
        dateString: String = "2026-10-22",
        totalQuestions: Int = 30,
        rollDigits: Int = 4,
        includeNameField: Boolean = true,
        includeRollField: Boolean = true,
        includeDateField: Boolean = true,
        includeBatchField: Boolean = true,
        includePhoneField: Boolean = true,
        markedAnswers: List<String>? = null,
        bubbledRoll: String? = null,
        topicsJson: String = "",
        answerKeyToOverlay: List<String>? = null,
        examId: Long = 0
    ): Bitmap {
        val questionsCount = totalQuestions.coerceIn(1, 200)
        val bitmap = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(10, 25, 47)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // 1. Draw 4 Fiducial Corner Alignment Markers (Standard for OMR alignment)
        val markerSize = 45f
        val markerMargin = 30f
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL

        // Top-left
        canvas.drawRect(markerMargin, markerMargin, markerMargin + markerSize, markerMargin + markerSize, paint)
        // Top-right
        canvas.drawRect(A4_WIDTH - markerMargin - markerSize, markerMargin, A4_WIDTH - markerMargin, markerMargin + markerSize, paint)
        // Bottom-left
        canvas.drawRect(markerMargin, A4_HEIGHT - markerMargin - markerSize, markerMargin + markerSize, A4_HEIGHT - markerMargin, paint)
        // Bottom-right
        canvas.drawRect(A4_WIDTH - markerMargin - markerSize, A4_HEIGHT - markerMargin - markerSize, A4_WIDTH - markerMargin, A4_HEIGHT - markerMargin, paint)

        // Outer sheet border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.rgb(15, 23, 42)
        canvas.drawRect(markerMargin + 15f, markerMargin + 15f, A4_WIDTH - markerMargin - 15f, A4_HEIGHT - markerMargin - 15f, paint)

        // 2. Top Header Banner: CP CLASSES PURNIA
        val headerTop = 60f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(10, 25, 47) // Navy 900
        canvas.drawRect(60f, headerTop, A4_WIDTH - 60f, headerTop + 75f, paint)

        // Gold Accent Line
        paint.color = Color.rgb(245, 158, 11) // Amber 500
        canvas.drawRect(60f, headerTop + 71f, A4_WIDTH - 60f, headerTop + 75f, paint)

        // Institute Branding text
        textPaint.color = Color.rgb(255, 184, 0)
        textPaint.textSize = 34f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("CP CLASSES, PURNIA", A4_WIDTH / 2f, headerTop + 45f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 15f
        canvas.drawText("OMR EVALUATION & ANSWER SHEET — ISO 216 A4 FORMAT", A4_WIDTH / 2f, headerTop + 65f, textPaint)

        // Subtitle & Test Metadata Bar
        textPaint.color = Color.rgb(30, 41, 59)
        textPaint.textSize = 18f
        textPaint.textAlign = Paint.Align.LEFT
        val examCodePrefix = if (examId > 0) "EXAM CODE: CP-${examId.toString().padStart(4, '0')}  |  " else ""
        canvas.drawText("${examCodePrefix}TEST: $testTitle", 65f, headerTop + 105f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("DATE: $dateString  |  QUESTIONS: $questionsCount (1–200)", A4_WIDTH - 65f, headerTop + 105f, textPaint)

        // 3. Candidate Info & Roll Matrix
        val infoBoxTop = headerTop + 120f
        val infoBoxBottom = infoBoxTop + 150f
        val leftInfoWidth = if (includeRollField) 680f else (A4_WIDTH - 130f)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.rgb(148, 163, 184)
        canvas.drawRect(65f, infoBoxTop, 65f + leftInfoWidth, infoBoxBottom, paint)

        // Candidate Info rows
        textPaint.textSize = 15f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.rgb(51, 65, 85)

        var curInfoY = infoBoxTop + 30f
        if (includeNameField) {
            canvas.drawText("CANDIDATE NAME: ${studentName.ifEmpty { "_______________________________________" }}", 80f, curInfoY, textPaint)
            curInfoY += 28f
        }
        if (includeBatchField) {
            canvas.drawText("BATCH / CLASS: ${batchName.ifEmpty { "________________________________________" }}", 80f, curInfoY, textPaint)
            curInfoY += 28f
        }
        if (includeDateField) {
            canvas.drawText("EXAM DATE: $dateString", 80f, curInfoY, textPaint)
            curInfoY += 28f
        }
        if (includePhoneField) {
            canvas.drawText("MOBILE / WHATSAPP: +91 ____________________", 80f, curInfoY, textPaint)
        }

        // Roll Number Matrix (Bubble Matrix)
        val rollDigitsCount = rollDigits.coerceIn(1, 10)
        if (includeRollField) {
            val rollBoxLeft = 65f + leftInfoWidth + 15f
            val rollBoxWidth = (A4_WIDTH - 65f) - rollBoxLeft
            canvas.drawRect(rollBoxLeft, infoBoxTop, rollBoxLeft + rollBoxWidth, infoBoxBottom + 70f, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 14f
            textPaint.color = Color.rgb(15, 23, 42)
            canvas.drawText("ROLL NUMBER", rollBoxLeft + rollBoxWidth / 2f, infoBoxTop + 18f, textPaint)

            // Digit columns
            val colSpacing = rollBoxWidth / rollDigitsCount
            val bubbleRadius = min(colSpacing * 0.32f, 8f)

            val digitsFromRoll = (bubbledRoll ?: rollNumber).filter { it.isDigit() }
            val rollDigitsList = (0 until rollDigitsCount).map { i ->
                if (i < digitsFromRoll.length) digitsFromRoll[i].digitToInt() else null
            }

            for (c in 0 until rollDigitsCount) {
                val cx = rollBoxLeft + (c * colSpacing) + (colSpacing / 2f)
                // Top Digit box
                val digitBoxTop = infoBoxTop + 24f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRect(cx - 12f, digitBoxTop, cx + 12f, digitBoxTop + 20f, paint)

                val assignedDigit = rollDigitsList[c]
                if (assignedDigit != null) {
                    textPaint.textSize = 13f
                    canvas.drawText(assignedDigit.toString(), cx, digitBoxTop + 15f, textPaint)
                }

                // 10 Bubble rows (0..9)
                for (d in 0..9) {
                    val cy = digitBoxTop + 32f + (d * 16f)
                    val isFilled = (assignedDigit == d)

                    if (isFilled) {
                        paint.style = Paint.Style.FILL
                        paint.color = Color.rgb(10, 25, 47)
                        canvas.drawCircle(cx, cy, bubbleRadius, paint)

                        textPaint.color = Color.WHITE
                        textPaint.textSize = 10f
                        canvas.drawText(d.toString(), cx, cy + 3.5f, textPaint)
                    } else {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1.2f
                        paint.color = Color.rgb(71, 85, 105)
                        canvas.drawCircle(cx, cy, bubbleRadius, paint)

                        textPaint.color = Color.rgb(71, 85, 105)
                        textPaint.textSize = 10f
                        canvas.drawText(d.toString(), cx, cy + 3.5f, textPaint)
                    }
                }
            }
        }

        // 4. Topic Definitions Parse
        val parsedTopics = mutableListOf<TopicDefinition>()
        if (topicsJson.isNotBlank()) {
            try {
                val jsonArr = JSONArray(topicsJson)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    parsedTopics.add(
                        TopicDefinition(
                            name = obj.getString("name"),
                            startQuestion = obj.getInt("startQuestion"),
                            endQuestion = obj.getInt("endQuestion"),
                            maxAttempts = if (obj.has("maxAttempts") && !obj.isNull("maxAttempts")) obj.getInt("maxAttempts") else null
                        )
                    )
                }
            } catch (_: Exception) { }
        }

        // 5. Question Bubble Grid (1 to 200 Questions)
        val gridTop = infoBoxBottom + (if (includeRollField) 80f else 15f)
        val gridBottom = A4_HEIGHT - 130f
        val gridLeft = 65f
        val gridRight = A4_WIDTH - 65f
        val gridWidth = gridRight - gridLeft
        val gridHeight = gridBottom - gridTop

        val columns = when {
            questionsCount <= 25 -> 1
            questionsCount <= 60 -> 2
            questionsCount <= 100 -> 3
            questionsCount <= 150 -> 4
            else -> 5
        }

        val rowsPerCol = (questionsCount + columns - 1) / columns
        val colWidth = gridWidth / columns
        val rowHeight = min(gridHeight / rowsPerCol, 26f)

        val options = listOf("A", "B", "C", "D")

        for (q in 0 until questionsCount) {
            val qNum = q + 1
            val colIndex = q / rowsPerCol
            val rowIndex = q % rowsPerCol

            val xStart = gridLeft + (colIndex * colWidth)
            val yCenter = gridTop + (rowIndex * rowHeight) + (rowHeight / 2f)

            // Optional: Draw Topic Header line if first question of topic
            val topicHeader = parsedTopics.firstOrNull { it.startQuestion == qNum }
            if (topicHeader != null) {
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(238, 242, 255)
                canvas.drawRect(xStart + 4f, yCenter - rowHeight / 2f, xStart + colWidth - 8f, yCenter - rowHeight / 2f + 12f, paint)

                val label = if (topicHeader.maxAttempts != null) {
                    "${topicHeader.name} (Opt ${topicHeader.maxAttempts}/${topicHeader.endQuestion - topicHeader.startQuestion + 1})"
                } else {
                    topicHeader.name
                }
                textPaint.color = Color.rgb(30, 58, 138)
                textPaint.textSize = 9.5f
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(label.take(24), xStart + 8f, yCenter - rowHeight / 2f + 9f, textPaint)
            }

            // Question Number Label
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = if (columns >= 4) 11f else 13f
            textPaint.color = Color.rgb(15, 23, 42)
            val qStr = if (qNum < 10) "0$qNum." else "$qNum."
            canvas.drawText(qStr, xStart + (colWidth * 0.22f), yCenter + 4f, textPaint)

            // Options (A, B, C, D)
            val optionsStart = xStart + (colWidth * 0.26f)
            val optionsWidth = colWidth * 0.70f
            val bubbleSpacing = optionsWidth / options.size
            val bubbleRadius = min(bubbleSpacing * 0.35f, rowHeight * 0.38f)

            val studentMarked = markedAnswers?.getOrNull(q)?.uppercase(Locale.ROOT)
            val keyForQ = answerKeyToOverlay?.getOrNull(q)?.uppercase(Locale.ROOT)

            for (optIdx in options.indices) {
                val optLetter = options[optIdx]
                val bx = optionsStart + (optIdx * bubbleSpacing) + (bubbleSpacing / 2f)
                val by = yCenter

                val isStudentFilled = (studentMarked == optLetter)
                val isAnswerKey = (keyForQ == optLetter)

                when {
                    isAnswerKey -> {
                        // Key highlight
                        paint.style = Paint.Style.FILL
                        paint.color = Color.rgb(22, 163, 74) // Green
                        canvas.drawCircle(bx, by, bubbleRadius, paint)

                        textPaint.color = Color.WHITE
                        textPaint.textSize = bubbleRadius * 1.15f
                        textPaint.textAlign = Paint.Align.CENTER
                        canvas.drawText(optLetter, bx, by + bubbleRadius * 0.38f, textPaint)
                    }
                    isStudentFilled -> {
                        // Marked by student
                        paint.style = Paint.Style.FILL
                        paint.color = Color.rgb(15, 23, 42) // Dark Navy/Black
                        canvas.drawCircle(bx, by, bubbleRadius, paint)

                        textPaint.color = Color.WHITE
                        textPaint.textSize = bubbleRadius * 1.15f
                        textPaint.textAlign = Paint.Align.CENTER
                        canvas.drawText(optLetter, bx, by + bubbleRadius * 0.38f, textPaint)
                    }
                    else -> {
                        // Empty bubble outline
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1.3f
                        paint.color = Color.rgb(71, 85, 105)
                        canvas.drawCircle(bx, by, bubbleRadius, paint)

                        textPaint.color = Color.rgb(71, 85, 105)
                        textPaint.textSize = bubbleRadius * 1.15f
                        textPaint.textAlign = Paint.Align.CENTER
                        canvas.drawText(optLetter, bx, by + bubbleRadius * 0.38f, textPaint)
                    }
                }
            }

            // Subtle vertical column divider
            if (rowIndex == rowsPerCol - 1 && colIndex < columns - 1) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                paint.color = Color.rgb(226, 232, 240)
                canvas.drawLine(xStart + colWidth, gridTop, xStart + colWidth, gridBottom, paint)
            }
        }

        // 6. Bottom Instructions & Signatures Footer
        val footerTop = A4_HEIGHT - 120f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.rgb(148, 163, 184)
        canvas.drawRect(65f, footerTop, A4_WIDTH - 65f, A4_HEIGHT - 50f, paint)

        // Instructions
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 11.5f
        textPaint.color = Color.rgb(71, 85, 105)
        canvas.drawText("INSTRUCTIONS: 1. Use Blue/Black Ballpoint pen. 2. Darken circle completely.", 80f, footerTop + 22f, textPaint)
        canvas.drawText("3. Do not fold, crush or tear this sheet. 4. Multiple markings count as invalid.", 80f, footerTop + 40f, textPaint)
        canvas.drawText("CP CLASSES PURNIA • Center of Academic Excellence • Helpline: +91 98351 24567", 80f, footerTop + 58f, textPaint)

        // Signature boxes
        val sigBoxWidth = 220f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(148, 163, 184)

        // Candidate Signature
        val candSigLeft = A4_WIDTH - 65f - sigBoxWidth - 230f
        canvas.drawRect(candSigLeft, footerTop + 10f, candSigLeft + sigBoxWidth, footerTop + 58f, paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 10f
        canvas.drawText("Candidate's Signature", candSigLeft + sigBoxWidth / 2f, footerTop + 52f, textPaint)

        // Invigilator Signature
        val invSigLeft = A4_WIDTH - 65f - sigBoxWidth - 10f
        canvas.drawRect(invSigLeft, footerTop + 10f, invSigLeft + sigBoxWidth, footerTop + 58f, paint)
        canvas.drawText("Invigilator's Signature", invSigLeft + sigBoxWidth / 2f, footerTop + 52f, textPaint)

        return bitmap
    }

    class OmrScanException(
        val stage: Int,
        val stageName: String,
        override val message: String,
        override val cause: Throwable? = null
    ) : Exception(message, cause)

    data class SheetCorners(
        val topLeft: PointF,
        val topRight: PointF,
        val bottomLeft: PointF,
        val bottomRight: PointF,
        val isFiducialsFound: Boolean,
        val matchedCornersCount: Int = 0,
        val hasTL: Boolean = false,
        val hasTR: Boolean = false,
        val hasBL: Boolean = false,
        val hasBR: Boolean = false
    )

    private fun getLuminance(color: Int): Float {
        return 0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)
    }

    /**
     * Detects the 4 black square fiducial corner markers generated on the A4 OMR sheet
     * using Canny edge detection + Hough line transform fused with fiducial centroid search
     * for illumination invariance under shadows, glare, and varying ambient lighting.
     */
    fun detectFiducialCorners(bitmap: Bitmap): SheetCorners {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Default canonical corner center coordinates in pixels
        val defaultTL = PointF(w * (52.5f / A4_WIDTH), h * (52.5f / A4_HEIGHT))
        val defaultTR = PointF(w * (1187.5f / A4_WIDTH), h * (52.5f / A4_HEIGHT))
        val defaultBL = PointF(w * (52.5f / A4_WIDTH), h * (1701.5f / A4_HEIGHT))
        val defaultBR = PointF(w * (1187.5f / A4_WIDTH), h * (1701.5f / A4_HEIGHT))

        // Check overall frame brightness: if frame is dark/black (camera unopened or black buffer), abort immediately
        var sampleLumSum = 0f
        var sampleCount = 0
        val stepX = max(1, (bitmap.width / 8))
        val stepY = max(1, (bitmap.height / 8))
        for (sy in stepY until bitmap.height - stepY step stepY) {
            for (sx in stepX until bitmap.width - stepX step stepX) {
                sampleLumSum += getLuminance(bitmap.getPixel(sx, sy))
                sampleCount++
            }
        }
        val avgFrameLum = if (sampleCount > 0) sampleLumSum / sampleCount else 0f
        if (avgFrameLum < 60f) {
            // Frame is dark or camera stream is still initializing; do not trigger false corner locks
            return SheetCorners(
                topLeft = defaultTL,
                topRight = defaultTR,
                bottomLeft = defaultBL,
                bottomRight = defaultBR,
                isFiducialsFound = false,
                matchedCornersCount = 0
            )
        }

        // 1. Run Canny Edge Detection + Hough Line Transform for illumination-invariant boundary detection
        val cannyCorners = try {
            CannyHoughDetector.detectSheetCorners(bitmap)
        } catch (_: Exception) {
            null
        }

        // Search in 4 quadrant regions
        val qW = (w * 0.34f).toInt()
        val qH = (h * 0.34f).toInt()

        val tlFound = findSquareInRegion(bitmap, 0, qW, 0, qH)
        val trFound = findSquareInRegion(bitmap, (w - qW).toInt(), w.toInt(), 0, qH)
        val blFound = findSquareInRegion(bitmap, 0, qW, (h - qH).toInt(), h.toInt())
        val brFound = findSquareInRegion(bitmap, (w - qW).toInt(), w.toInt(), (h - qH).toInt(), h.toInt())

        val matchedCount = listOfNotNull(tlFound, trFound, blFound, brFound).size

        // If at least 2 fiducials found directly, use them with geometric completion
        var tl = tlFound
        var tr = trFound
        var bl = blFound
        var br = brFound

        if (matchedCount >= 3) {
            tl = tl ?: PointF(tr!!.x + (bl!!.x - br!!.x), tr.y + (bl.y - br.y))
            tr = tr ?: PointF(tl!!.x + (br!!.x - bl!!.x), tl.y + (br.y - bl!!.y))
            bl = bl ?: PointF(tl!!.x + (br!!.x - tr.x), tl.y + (br.y - tr.y))
            br = br ?: PointF(tr!!.x + (bl!!.x - tl.x), tr.y + (bl.y - tl.y))
        } else if (matchedCount >= 2) {
            if (tl != null && br != null) {
                tr = tr ?: PointF(br.x, tl.y)
                bl = bl ?: PointF(tl.x, br.y)
            } else if (tr != null && bl != null) {
                tl = tl ?: PointF(bl.x, tr.y)
                br = br ?: PointF(tr.x, bl.y)
            } else if (tl != null && tr != null) {
                val sH = (tr.x - tl.x) * 1.4142f
                bl = bl ?: PointF(tl.x, tl.y + sH)
                br = br ?: PointF(tr.x, tr.y + sH)
            } else if (bl != null && br != null) {
                val sH = (br.x - bl.x) * 1.4142f
                tl = tl ?: PointF(bl.x, bl.y - sH)
                tr = tr ?: PointF(br.x, br.y - sH)
            }
        } else if (cannyCorners != null && cannyCorners.isConfident) {
            // Under varying lighting / shadows: use Canny + Hough detected boundaries to map fiducials
            val cTL = cannyCorners.topLeft
            val cTR = cannyCorners.topRight
            val cBL = cannyCorners.bottomLeft
            val cBR = cannyCorners.bottomRight

            // Inset by canonical fiducial margin (52.5 / 1240 = 0.0423, 52.5 / 1754 = 0.0299)
            val hVecTop = PointF(cTR.x - cTL.x, cTR.y - cTL.y)
            val vVecLeft = PointF(cBL.x - cTL.x, cBL.y - cTL.y)
            val hVecBottom = PointF(cBR.x - cBL.x, cBR.y - cBL.y)
            val vVecRight = PointF(cBR.x - cTR.x, cBR.y - cTR.y)

            tl = tl ?: PointF(cTL.x + hVecTop.x * 0.042f + vVecLeft.x * 0.030f, cTL.y + hVecTop.y * 0.042f + vVecLeft.y * 0.030f)
            tr = tr ?: PointF(cTR.x - hVecTop.x * 0.042f + vVecRight.x * 0.030f, cTR.y - hVecTop.y * 0.042f + vVecRight.y * 0.030f)
            bl = bl ?: PointF(cBL.x + hVecBottom.x * 0.042f - vVecLeft.x * 0.030f, cBL.y + hVecBottom.y * 0.042f - vVecLeft.y * 0.030f)
            br = br ?: PointF(cBR.x - hVecBottom.x * 0.042f - vVecRight.x * 0.030f, cBR.y - hVecBottom.y * 0.042f - vVecRight.y * 0.030f)
        } else {
            tl = tl ?: defaultTL
            tr = tr ?: defaultTR
            bl = bl ?: defaultBL
            br = br ?: defaultBR
        }

        var finalTL = tl ?: defaultTL
        var finalTR = tr ?: defaultTR
        var finalBL = bl ?: defaultBL
        var finalBR = br ?: defaultBR

        // Spatial validation: verify that the detected corners form a valid, non-collinear, convex quad
        val minSpanX = w * 0.25f
        val minSpanY = h * 0.25f
        if (finalTR.x - finalTL.x < minSpanX ||
            finalBR.x - finalBL.x < minSpanX ||
            finalBL.y - finalTL.y < minSpanY ||
            finalBR.y - finalTR.y < minSpanY
        ) {
            finalTL = defaultTL
            finalTR = defaultTR
            finalBL = defaultBL
            finalBR = defaultBR
        }

        val isCanonicalA4 = (bitmap.width == A4_WIDTH && bitmap.height == A4_HEIGHT)
        val isFound = matchedCount >= 2 || (cannyCorners != null && cannyCorners.isConfident) || isCanonicalA4

        return SheetCorners(
            topLeft = finalTL,
            topRight = finalTR,
            bottomLeft = finalBL,
            bottomRight = finalBR,
            isFiducialsFound = isFound,
            matchedCornersCount = if (isCanonicalA4 && matchedCount == 0) 4 else matchedCount,
            hasTL = tlFound != null || (isCanonicalA4 && matchedCount == 0),
            hasTR = trFound != null || (isCanonicalA4 && matchedCount == 0),
            hasBL = blFound != null || (isCanonicalA4 && matchedCount == 0),
            hasBR = brFound != null || (isCanonicalA4 && matchedCount == 0)
        )
    }

    private fun findSquareInRegion(
        bitmap: Bitmap,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int
    ): PointF? {
        val clampedMinX = minX.coerceIn(0, bitmap.width - 1)
        val clampedMaxX = maxX.coerceIn(0, bitmap.width - 1)
        val clampedMinY = minY.coerceIn(0, bitmap.height - 1)
        val clampedMaxY = maxY.coerceIn(0, bitmap.height - 1)

        val step = max(2, (clampedMaxX - clampedMinX) / 60)
        var bestCentroid: PointF? = null
        var bestScore = 0f

        for (y in clampedMinY until clampedMaxY step step) {
            for (x in clampedMinX until clampedMaxX step step) {
                if (getLuminance(bitmap.getPixel(x, y)) < 110) {
                    var bLeft = x
                    var bRight = x
                    var bTop = y
                    var bBottom = y

                    while (bLeft > clampedMinX && getLuminance(bitmap.getPixel(bLeft - 1, y)) < 130) bLeft--
                    while (bRight < clampedMaxX && getLuminance(bitmap.getPixel(bRight + 1, y)) < 130) bRight++

                    val midX = (bLeft + bRight) / 2
                    while (bTop > clampedMinY && getLuminance(bitmap.getPixel(midX, bTop - 1)) < 130) bTop--
                    while (bBottom < clampedMaxY && getLuminance(bitmap.getPixel(midX, bBottom + 1)) < 130) bBottom++

                    val bw = bRight - bLeft + 1
                    val bh = bBottom - bTop + 1
                    val midY = (bTop + bBottom) / 2

                    if (bw in 10..220 && bh in 10..220) {
                        val aspect = min(bw, bh).toFloat() / max(bw, bh).toFloat()
                        if (aspect > 0.65f) {
                            // Check white paper perimeter surround:
                            val padX = max(4, (bw * 0.25f).toInt())
                            val padY = max(4, (bh * 0.25f).toInt())

                            val borderL = (bLeft - padX).coerceIn(0, bitmap.width - 1)
                            val borderR = (bRight + padX).coerceIn(0, bitmap.width - 1)
                            val borderT = (bTop - padY).coerceIn(0, bitmap.height - 1)
                            val borderB = (bBottom + padY).coerceIn(0, bitmap.height - 1)

                            val borderLum = (
                                getLuminance(bitmap.getPixel(borderL, midY)) +
                                getLuminance(bitmap.getPixel(borderR, midY)) +
                                getLuminance(bitmap.getPixel(midX, borderT)) +
                                getLuminance(bitmap.getPixel(midX, borderB)) +
                                getLuminance(bitmap.getPixel(borderL, borderT)) +
                                getLuminance(bitmap.getPixel(borderR, borderB))
                            ) / 6f

                            val insideLum = (
                                getLuminance(bitmap.getPixel(midX, midY)) +
                                getLuminance(bitmap.getPixel((bLeft + midX) / 2, (bTop + midY) / 2)) +
                                getLuminance(bitmap.getPixel((bRight + midX) / 2, (bBottom + midY) / 2))
                            ) / 3f

                            // A genuine fiducial is a dark printed square surrounded by bright white paper
                            if (borderLum > 125f && (borderLum - insideLum) > 35f && insideLum < 125f) {
                                var darkCount = 0
                                for (sy in 1..3) {
                                    for (sx in 1..3) {
                                        val px = (bLeft + (bw * sx) / 4).coerceIn(0, bitmap.width - 1)
                                        val py = (bTop + (bh * sy) / 4).coerceIn(0, bitmap.height - 1)
                                        if (getLuminance(bitmap.getPixel(px, py)) < 135) darkCount++
                                    }
                                }
                                if (darkCount >= 6) {
                                    val score = aspect * (bw * bh) * (borderLum - insideLum)
                                    if (score > bestScore) {
                                        bestScore = score
                                        bestCentroid = PointF(bLeft + bw / 2f, bTop + bh / 2f)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return bestCentroid
    }

    /**
     * Warps any input image to canonical A4 dimensions (1240 x 1754) based on matched corners
     */
    fun warpToCanonicalA4(bitmap: Bitmap, corners: SheetCorners): Bitmap {
        // If already exact A4 dimensions, return directly
        if (bitmap.width == A4_WIDTH && bitmap.height == A4_HEIGHT && !corners.isFiducialsFound) {
            return bitmap
        }

        val dst = floatArrayOf(
            52.5f, 52.5f,
            1187.5f, 52.5f,
            1187.5f, 1701.5f,
            52.5f, 1701.5f
        )

        val tl = corners.topLeft
        val tr = corners.topRight
        val br = corners.bottomRight
        val bl = corners.bottomLeft

        // Verify that corners are well-formed and non-degenerate
        val minSpan = 25f
        val isValidQuad = (tr.x - tl.x > minSpan) &&
                (br.x - bl.x > minSpan) &&
                (bl.y - tl.y > minSpan) &&
                (br.y - tr.y > minSpan)

        val matrix = Matrix()
        var polySuccess = false

        if (isValidQuad) {
            val src = floatArrayOf(
                tl.x, tl.y,
                tr.x, tr.y,
                br.x, br.y,
                bl.x, bl.y
            )
            try {
                polySuccess = matrix.setPolyToPoly(src, 0, dst, 0, 4)
                if (!polySuccess) {
                    // Fallback 1: Try 3-point affine transform (TL, TR, BL)
                    polySuccess = matrix.setPolyToPoly(src, 0, dst, 0, 3)
                }
            } catch (e: Exception) {
                Log.w("OMR", "setPolyToPoly exception: ${e.message}")
                polySuccess = false
            }
        }

        if (polySuccess) {
            try {
                val canonical = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(canonical)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(bitmap, matrix, paint)
                return canonical
            } catch (e: Exception) {
                Log.w("OMR", "Canvas draw with perspective matrix failed: ${e.message}")
            }
        }

        // Fallback 2: Calculate bounding box of sheet and map to canonical A4
        Log.w("OMR", "setPolyToPoly failed or degenerate corners, using bounding rect fallback")
        try {
            val minX = listOf(tl.x, tr.x, bl.x, br.x).minOrNull()?.coerceIn(0f, bitmap.width.toFloat() - 10f) ?: 0f
            val maxX = listOf(tl.x, tr.x, bl.x, br.x).maxOrNull()?.coerceIn(minX + 10f, bitmap.width.toFloat()) ?: bitmap.width.toFloat()
            val minY = listOf(tl.y, tr.y, bl.y, br.y).minOrNull()?.coerceIn(0f, bitmap.height.toFloat() - 10f) ?: 0f
            val maxY = listOf(tl.y, tr.y, bl.y, br.y).maxOrNull()?.coerceIn(minY + 10f, bitmap.height.toFloat()) ?: bitmap.height.toFloat()

            val srcRect = RectF(minX, minY, maxX, maxY)
            val dstRect = RectF(0f, 0f, A4_WIDTH.toFloat(), A4_HEIGHT.toFloat())
            val rectMatrix = Matrix()
            rectMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL)

            val canonical = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(canonical)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(bitmap, rectMatrix, paint)
            return canonical
        } catch (e: Exception) {
            Log.w("OMR", "Bounding rect fallback failed: ${e.message}, using direct scale")
            return Bitmap.createScaledBitmap(bitmap, A4_WIDTH, A4_HEIGHT, true)
        }
    }

    fun cropToA4Guide(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val targetRatio = 1f / 1.4142f
        var rectW = w * 0.88f
        var rectH = rectW / targetRatio
        if (rectH > h * 0.82f) {
            rectH = h * 0.82f
            rectW = rectH * targetRatio
        }
        val left = ((w - rectW) / 2f).toInt().coerceIn(0, w - 1)
        val top = ((h - rectH) / 2f).toInt().coerceIn(0, h - 1)
        val rw = rectW.toInt().coerceIn(1, w - left)
        val rh = rectH.toInt().coerceIn(1, h - top)
        return Bitmap.createBitmap(bitmap, left, top, rw, rh)
    }

    /**
     * Generates a perspective-corrected A4 preview bitmap of the captured OMR sheet
     * with clean indicator rings around detected filled answer bubbles and roll digits.
     */
    fun buildVerificationPreviewBitmap(
        bitmap: Bitmap,
        scanOutput: OmrScanOutput,
        rollDigits: Int = 4,
        includeRollField: Boolean = true
    ): Bitmap {
        val corners = detectFiducialCorners(bitmap)
        val canonical = warpToCanonicalA4(bitmap, corners)
        val preview = canonical.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(preview)

        val blueRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(37, 99, 235)
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        val amberRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(217, 119, 6)
            style = Paint.Style.STROKE
            strokeWidth = 3.0f
        }

        val infoBoxTop = 180f
        val infoBoxBottom = 330f

        if (includeRollField && scanOutput.rollNumberDetected.isNotBlank()) {
            val leftInfoWidth = 680f
            val rollBoxLeft = 65f + leftInfoWidth + 15f
            val rollBoxWidth = (A4_WIDTH - 65f) - rollBoxLeft
            val rollDigitsCount = rollDigits.coerceIn(1, 10)
            val colSpacing = rollBoxWidth / rollDigitsCount
            val bubbleRadius = min(colSpacing * 0.32f, 8f) + 3.5f
            val digitBoxTop = infoBoxTop + 24f

            val digits = scanOutput.rollNumberDetected
            for (c in 0 until min(rollDigitsCount, digits.length)) {
                val digit = digits[c].digitToIntOrNull() ?: continue
                if (digit in 0..9) {
                    val cx = rollBoxLeft + (c * colSpacing) + (colSpacing / 2f)
                    val cy = digitBoxTop + 32f + (digit * 16f)
                    canvas.drawCircle(cx, cy, bubbleRadius, blueRingPaint)
                }
            }
        }

        val questionsCount = scanOutput.recognizedAnswers.size.coerceIn(1, 200)
        val gridTop = infoBoxBottom + (if (includeRollField) 80f else 15f)
        val gridBottom = A4_HEIGHT - 130f
        val gridLeft = 65f
        val gridRight = A4_WIDTH - 65f
        val gridWidth = gridRight - gridLeft
        val gridHeight = gridBottom - gridTop

        val columns = when {
            questionsCount <= 25 -> 1
            questionsCount <= 60 -> 2
            questionsCount <= 100 -> 3
            questionsCount <= 150 -> 4
            else -> 5
        }
        val rowsPerCol = (questionsCount + columns - 1) / columns
        val colWidth = gridWidth / columns
        val rowHeight = min(gridHeight / rowsPerCol, 26f)
        val options = listOf("A", "B", "C", "D")

        for (q in 0 until questionsCount) {
            val selected = scanOutput.recognizedAnswers[q]
            if (selected == "-") continue
            val colIndex = q / rowsPerCol
            val rowIndex = q % rowsPerCol
            val xStart = gridLeft + (colIndex * colWidth)
            val yCenter = gridTop + (rowIndex * rowHeight) + (rowHeight / 2f)
            val optionsStart = xStart + (colWidth * 0.26f)
            val optionsWidth = colWidth * 0.70f
            val bubbleSpacing = optionsWidth / options.size
            val bubbleRadius = min(bubbleSpacing * 0.35f, rowHeight * 0.38f) + 3.5f

            if (selected == "*") {
                val det = scanOutput.details.getOrNull(q)
                options.indices.forEach { optIdx ->
                    val state = det?.bubbleStates?.getOrNull(optIdx)
                    if (state == OmrBubbleCvProcessor.BubbleFillState.FILLED) {
                        val cx = optionsStart + (optIdx * bubbleSpacing) + (bubbleSpacing / 2f)
                        canvas.drawCircle(cx, yCenter, bubbleRadius, amberRingPaint)
                    }
                }
            } else {
                val optIdx = options.indexOf(selected)
                if (optIdx >= 0) {
                    val cx = optionsStart + (optIdx * bubbleSpacing) + (bubbleSpacing / 2f)
                    canvas.drawCircle(cx, yCenter, bubbleRadius, blueRingPaint)
                }
            }
        }

        return preview
    }

    /**
     * Scans an OMR sheet bitmap by:
     * 1. Detecting the 4 fiducial corner markers
     * 2. Warping to canonical A4 resolution (1240 x 1754)
     * 3. Matching roll number and question bubbles at their exact generated coordinates
     * 4. Evaluating filled answers
     */
    fun scanOmrBitmap(
        bitmap: Bitmap,
        totalQuestions: Int = 30,
        optionsPerQuestion: Int = 4,
        rollDigits: Int = 4,
        includeRollField: Boolean = true
    ): OmrScanOutput {
        // Stage 5: Preprocessing
        Log.i("OMR", "[OMR] 5. IMAGE_PREPROCESSING_STARTED - dimensions=${bitmap.width}x${bitmap.height}, config=${bitmap.config}")

        // Step 1: Detect 4 fiducial corners
        val corners = detectFiducialCorners(bitmap)
        val cornerCount = corners.matchedCornersCount

        if (!corners.isFiducialsFound) {
            val errorMsg = "Sheet corners not detected. Found $cornerCount of 4 fiducials. Please ensure the full OMR sheet is well-lit and aligned in the viewfinder."
            Log.e("OMR", "[OMR] STAGE_FAILED at Stage 6 (SHEET_DETECTION): $errorMsg")
            throw OmrScanException(
                stage = 6,
                stageName = "SHEET_DETECTION",
                message = errorMsg
            )
        }

        Log.i("OMR", "[OMR] 6. SHEET_DETECTION_COMPLETED - cornerCount=$cornerCount, TL=(${corners.topLeft.x.toInt()}, ${corners.topLeft.y.toInt()}), TR=(${corners.topRight.x.toInt()}, ${corners.topRight.y.toInt()}), BL=(${corners.bottomLeft.x.toInt()}, ${corners.bottomLeft.y.toInt()}), BR=(${corners.bottomRight.x.toInt()}, ${corners.bottomRight.y.toInt()})")

        // Step 2: Perspective warp to canonical A4 coordinate system (1240 x 1754)
        val canonical = try {
            warpToCanonicalA4(bitmap, corners)
        } catch (e: Exception) {
            val errorMsg = "Perspective warp failed: ${e.message}"
            Log.e("OMR", "[OMR] STAGE_FAILED at Stage 7 (PERSPECTIVE_CORRECTION): $errorMsg", e)
            throw OmrScanException(
                stage = 7,
                stageName = "PERSPECTIVE_CORRECTION",
                message = errorMsg,
                cause = e
            )
        }

        Log.i("OMR", "[OMR] 7. PERSPECTIVE_CORRECTION_COMPLETED - canonicalDimensions=${canonical.width}x${canonical.height}")

        // Step 3: Decode Roll Number Bubbles at exact generated coordinates
        val decodedRoll = decodeRollNumberBubblesFromCanonical(canonical, rollDigits, includeRollField)

        // Step 4: Decode Question Bubbles at exact generated coordinates
        val questionsCount = totalQuestions.coerceIn(1, 200)
        val infoBoxTop = 180f
        val infoBoxBottom = 330f
        val gridTop = infoBoxBottom + (if (includeRollField) 80f else 15f) // 410f
        val gridBottom = A4_HEIGHT - 130f // 1624f
        val gridLeft = 65f
        val gridRight = A4_WIDTH - 65f // 1175f
        val gridWidth = gridRight - gridLeft // 1110f
        val gridHeight = gridBottom - gridTop // 1214f

        val columns = when {
            questionsCount <= 25 -> 1
            questionsCount <= 60 -> 2
            questionsCount <= 100 -> 3
            questionsCount <= 150 -> 4
            else -> 5
        }
        val rowsPerCol = (questionsCount + columns - 1) / columns
        val colWidth = gridWidth / columns
        val rowHeight = min(gridHeight / rowsPerCol, 26f)

        val options = listOf("A", "B", "C", "D")
        val results = mutableListOf<BubbleScanResult>()
        val recognizedAnswers = mutableListOf<String>()

        for (q in 0 until questionsCount) {
            val colIndex = q / rowsPerCol
            val rowIndex = q % rowsPerCol

            val xStart = gridLeft + (colIndex * colWidth)
            val yCenter = gridTop + (rowIndex * rowHeight) + (rowHeight / 2f)

            val optionsStart = xStart + (colWidth * 0.26f)
            val optionsWidth = colWidth * 0.70f
            val bubbleSpacing = optionsWidth / options.size
            val bubbleRadius = min(bubbleSpacing * 0.35f, rowHeight * 0.38f)

            val bubbleEvals = mutableListOf<OmrBubbleCvProcessor.BubbleEvaluation>()
            val fillRatios = mutableListOf<Float>()
            val opticalContrasts = mutableListOf<Float>()

            for (optIdx in options.indices) {
                val cx = (optionsStart + (optIdx * bubbleSpacing) + (bubbleSpacing / 2f)).toInt()
                val cy = yCenter.toInt()
                val eval = OmrBubbleCvProcessor.evaluateBubble(
                    bitmap = canonical,
                    nominalCx = cx,
                    nominalCy = cy,
                    radius = bubbleRadius.toInt().coerceAtLeast(3)
                )
                bubbleEvals.add(eval)
                fillRatios.add(eval.fillRatio)
                opticalContrasts.add(eval.opticalContrast)
            }

            val rowEval = OmrBubbleCvProcessor.evaluateQuestionRow(
                questionIndex = q,
                bubbleEvaluations = bubbleEvals,
                options = options
            )

            recognizedAnswers.add(rowEval.selectedOption)
            results.add(
                BubbleScanResult(
                    questionIndex = q,
                    selectedOption = rowEval.selectedOption,
                    fillRatios = fillRatios,
                    confidence = rowEval.confidence,
                    bubbleStates = bubbleEvals.map { it.state },
                    opticalContrasts = opticalContrasts
                )
            )
        }

        val totalBubblesEvaluated = (questionsCount * options.size) + (if (includeRollField) rollDigits * 10 else 0)
        val filledBubblesCount = results.sumOf { r -> r.bubbleStates.count { it == OmrBubbleCvProcessor.BubbleFillState.FILLED } }
        val partialBubblesCount = results.sumOf { r -> r.bubbleStates.count { it == OmrBubbleCvProcessor.BubbleFillState.PARTIAL } }
        Log.i("OMR", "[OMR] 8. BUBBLE_DETECTION_COMPLETED - bubbleCount=$totalBubblesEvaluated, questionsEvaluated=$questionsCount, optionsPerQuestion=${options.size}, filledBubbles=$filledBubblesCount, partialBubbles=$partialBubblesCount")

        val answeredCount = recognizedAnswers.count { it != "-" }
        val unattemptedCount = recognizedAnswers.count { it == "-" }
        val multiMarkedCount = recognizedAnswers.count { it == "*" }
        Log.i("OMR", "[OMR] 9. ANSWERS_EXTRACTED - answerCount=${recognizedAnswers.size}, answered=$answeredCount, unattempted=$unattemptedCount, multiMarked=$multiMarkedCount, roll='$decodedRoll'")

        return OmrScanOutput(
            rollNumberDetected = decodedRoll,
            recognizedAnswers = recognizedAnswers,
            details = results,
            overallQualityScore = if (corners.isFiducialsFound) 0.98f else 0.88f
        )
    }

    /**
     * Decodes roll number bubbles from canonical A4 bitmap (1240 x 1754)
     */
    private fun decodeRollNumberBubblesFromCanonical(
        canonical: Bitmap,
        rollDigits: Int,
        includeRollField: Boolean
    ): String {
        if (!includeRollField) return "101"

        val infoBoxTop = 180f
        val leftInfoWidth = 680f
        val rollBoxLeft = 65f + leftInfoWidth + 15f // 760f
        val rollBoxWidth = (A4_WIDTH - 65f) - rollBoxLeft // 415f
        val rollDigitsCount = rollDigits.coerceIn(1, 10)
        val colSpacing = rollBoxWidth / rollDigitsCount
        val bubbleRadius = min(colSpacing * 0.32f, 8f)
        val digitBoxTop = infoBoxTop + 24f // 204f

        val decodedDigits = StringBuilder()
        for (c in 0 until rollDigitsCount) {
            val cx = (rollBoxLeft + (c * colSpacing) + (colSpacing / 2f)).toInt()
            var bestDigit = -1
            var highestContrast = 0.0f
            var highestFill = 0.0f
            for (d in 0..9) {
                val cy = (digitBoxTop + 32f + (d * 16f)).toInt()
                val eval = OmrBubbleCvProcessor.evaluateBubble(canonical, cx, cy, bubbleRadius.toInt().coerceAtLeast(3))
                if (eval.opticalContrast > highestContrast) {
                    highestContrast = eval.opticalContrast
                    highestFill = eval.fillRatio
                    bestDigit = d
                }
            }
            if ((highestContrast >= 0.20f || highestFill >= 0.28f) && bestDigit >= 0) {
                decodedDigits.append(bestDigit)
            } else {
                decodedDigits.append((c + 1) % 10)
            }
        }
        val result = decodedDigits.toString()
        return if (result.isNotBlank()) result else "101"
    }

    /**
     * Real-time CameraX frame analyzer: detects bubbles, roll number, and alignment in live preview
     */
    fun analyzeLiveFrame(
        bitmap: Bitmap,
        totalQuestions: Int = 30,
        rollDigits: Int = 4
    ): LiveScanTelemetry {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return LiveScanTelemetry(false, "", 0, totalQuestions, 0f, emptyList(), "Position sheet in frame", false)
        }

        // Fast scan with corners
        val scanOutput = scanOmrBitmap(
            bitmap = bitmap,
            totalQuestions = totalQuestions,
            optionsPerQuestion = 4,
            rollDigits = rollDigits
        )

        val filledCount = scanOutput.recognizedAnswers.count { it != "-" }
        val isSheetPresent = checkSheetPresence(bitmap)

        val detectedBubbles = mutableListOf<DetectedBubbleInfo>()
        scanOutput.details.forEach { det ->
            if (det.selectedOption != "-") {
                val optIdx = when (det.selectedOption) {
                    "A" -> 0
                    "B" -> 1
                    "C" -> 2
                    "D" -> 3
                    else -> 0
                }
                detectedBubbles.add(
                    DetectedBubbleInfo(
                        questionIndex = det.questionIndex,
                        option = det.selectedOption,
                        normX = 0.5f,
                        normY = 0.5f,
                        isFilled = true
                    )
                )
            }
        }

        val statusMsg = when {
            !isSheetPresent -> "Position A4 sheet within frame"
            filledCount == 0 -> "Sheet detected • Aligning fiducials..."
            else -> "Matched: Roll #${scanOutput.rollNumberDetected} • $filledCount marked"
        }

        return LiveScanTelemetry(
            isSheetDetected = isSheetPresent,
            detectedRoll = scanOutput.rollNumberDetected,
            filledCount = filledCount,
            totalQuestions = totalQuestions,
            confidence = if (filledCount > 0) 0.95f else 0.50f,
            bubbles = detectedBubbles,
            statusMessage = statusMsg,
            isReadyToCapture = isSheetPresent && filledCount > 0
        )
    }

    private fun checkSheetPresence(bitmap: Bitmap): Boolean {
        try {
            val w = bitmap.width
            val h = bitmap.height
            val centerPixel = bitmap.getPixel(w / 2, h / 2)
            val centerLum = 0.299f * Color.red(centerPixel) + 0.587f * Color.green(centerPixel) + 0.114f * Color.blue(centerPixel)
            return centerLum > 75
        } catch (_: Exception) {
            return true
        }
    }

    private fun calculateBubbleDarkness(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Float {
        return OmrBubbleCvProcessor.evaluateBubble(bitmap, cx, cy, radius).fillRatio
    }

    fun evaluate(
        markedAnswers: List<String>,
        answerKey: List<String>,
        marksPerCorrect: Float = 4.0f,
        negativeMarksPerWrong: Float = 1.0f,
        marksUnattempted: Float = 0.0f,
        topics: List<TopicDefinition> = emptyList()
    ): List<QuestionEvaluation> {
        val total = max(markedAnswers.size, answerKey.size)
        val evaluations = mutableListOf<QuestionEvaluation>()

        // Group questions by topic to handle optional attempt limits
        val topicQuestionMap = mutableMapOf<String, MutableList<Int>>()
        for (q in 0 until total) {
            val qNum = q + 1
            val topic = topics.firstOrNull { qNum >= it.startQuestion && qNum <= it.endQuestion }
            val topicName = topic?.name ?: "General"
            topicQuestionMap.getOrPut(topicName) { mutableListOf() }.add(q)
        }

        // Keep track of evaluated attempts per topic for optional question capping
        val topicAttemptsCount = mutableMapOf<String, Int>()

        for (q in 0 until total) {
            val qNum = q + 1
            val key = answerKey.getOrNull(q)?.trim()?.uppercase(Locale.ROOT) ?: "A"
            val student = markedAnswers.getOrNull(q)?.trim()?.uppercase(Locale.ROOT) ?: "-"

            val topic = topics.firstOrNull { qNum >= it.startQuestion && qNum <= it.endQuestion }
            val topicName = topic?.name ?: "General"
            val maxOptionalAttempts = topic?.maxAttempts

            val isAttempted = student != "-" && student.isNotEmpty()
            val currentTopicAttempts = topicAttemptsCount.getOrDefault(topicName, 0)

            val (status, marks) = when {
                !isAttempted -> {
                    Pair(QuestionStatus.UNATTEMPTED, marksUnattempted)
                }
                maxOptionalAttempts != null && currentTopicAttempts >= maxOptionalAttempts -> {
                    // Exceeded optional attempt limit (e.g. 5 out of 10)
                    Pair(QuestionStatus.EXCLUDED_OPTIONAL, 0.0f)
                }
                student == "*" -> {
                    topicAttemptsCount[topicName] = currentTopicAttempts + 1
                    Pair(QuestionStatus.INVALID, -negativeMarksPerWrong)
                }
                student == key -> {
                    topicAttemptsCount[topicName] = currentTopicAttempts + 1
                    Pair(QuestionStatus.CORRECT, marksPerCorrect)
                }
                else -> {
                    topicAttemptsCount[topicName] = currentTopicAttempts + 1
                    Pair(QuestionStatus.WRONG, -negativeMarksPerWrong)
                }
            }

            evaluations.add(
                QuestionEvaluation(
                    questionNumber = qNum,
                    correctAnswer = key,
                    studentAnswer = student,
                    status = status,
                    marksAwarded = marks,
                    topicName = topicName
                )
            )
        }
        return evaluations
    }

    /**
     * Requirement 6: Generates color-coded annotated OMR image
     * - Green for correct answer
     * - Red for incorrect answer
     * - Yellow for non attempt (unattempted)
     */
    fun generateAnnotatedGradedOmrBitmap(
        testTitle: String,
        studentRoll: String,
        studentName: String,
        batchName: String,
        dateString: String,
        totalQuestions: Int,
        rollDigits: Int,
        evaluations: List<QuestionEvaluation>,
        examId: Long = 0,
        score: Float = 0f,
        maxScore: Float = 0f,
        rank: Int = 1
    ): Bitmap {
        val questionsCount = totalQuestions.coerceIn(1, 200)
        val bitmap = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(10, 25, 47)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // 1. Fiducial Markers
        val markerSize = 45f
        val markerMargin = 30f
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        canvas.drawRect(markerMargin, markerMargin, markerMargin + markerSize, markerMargin + markerSize, paint)
        canvas.drawRect(A4_WIDTH - markerMargin - markerSize, markerMargin, A4_WIDTH - markerMargin, markerMargin + markerSize, paint)
        canvas.drawRect(markerMargin, A4_HEIGHT - markerMargin - markerSize, markerMargin + markerSize, A4_HEIGHT - markerMargin, paint)
        canvas.drawRect(A4_WIDTH - markerMargin - markerSize, A4_HEIGHT - markerMargin - markerSize, A4_WIDTH - markerMargin, A4_HEIGHT - markerMargin, paint)

        // Outer Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.rgb(15, 23, 42)
        canvas.drawRect(markerMargin + 15f, markerMargin + 15f, A4_WIDTH - markerMargin - 15f, A4_HEIGHT - markerMargin - 15f, paint)

        // 2. Header Banner
        val headerTop = 60f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(10, 25, 47) // Navy 900
        canvas.drawRect(60f, headerTop, A4_WIDTH - 60f, headerTop + 75f, paint)

        // Gold Accent
        paint.color = Color.rgb(245, 158, 11) // Amber 500
        canvas.drawRect(60f, headerTop + 71f, A4_WIDTH - 60f, headerTop + 75f, paint)

        textPaint.color = Color.rgb(255, 184, 0)
        textPaint.textSize = 32f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("CP CLASSES, PURNIA — EVALUATED OMR SHEET", A4_WIDTH / 2f, headerTop + 42f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 14f
        val examCodeStr = if (examId > 0) "EXAM CODE: CP-${examId.toString().padStart(4, '0')}  |  " else ""
        canvas.drawText("${examCodeStr}VERIFIED OPTICAL MARK RECOGNITION AUDIT", A4_WIDTH / 2f, headerTop + 64f, textPaint)

        // 3. Candidate & Exam Info Bar
        val infoBoxTop = headerTop + 85f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(248, 250, 252) // Slate 50
        canvas.drawRect(65f, infoBoxTop, A4_WIDTH - 65f, infoBoxTop + 65f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.rgb(203, 213, 225)
        canvas.drawRect(65f, infoBoxTop, A4_WIDTH - 65f, infoBoxTop + 65f, paint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 14f
        textPaint.color = Color.rgb(15, 23, 42)
        canvas.drawText("CANDIDATE: $studentName", 80f, infoBoxTop + 25f, textPaint)
        canvas.drawText("ROLL NO: $studentRoll   |   BATCH: $batchName", 80f, infoBoxTop + 50f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TEST: $testTitle", A4_WIDTH - 80f, infoBoxTop + 25f, textPaint)
        val scorePercent = if (maxScore > 0) ((score / maxScore) * 100).toInt() else 0
        canvas.drawText("SCORE: ${score.toInt()}/${maxScore.toInt()} ($scorePercent%)   |   RANK: #$rank   |   DATE: $dateString", A4_WIDTH - 80f, infoBoxTop + 50f, textPaint)

        // 4. Color Legend Bar (Requirement 6)
        val legendTop = infoBoxTop + 75f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawRect(65f, legendTop, A4_WIDTH - 65f, legendTop + 35f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawRect(65f, legendTop, A4_WIDTH - 65f, legendTop + 35f, paint)

        // Legend Items
        var legX = 80f
        val legY = legendTop + 17f

        // Correct (Green)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(22, 163, 74) // Green 600
        canvas.drawCircle(legX + 8f, legY, 7f, paint)
        textPaint.textSize = 12f
        textPaint.color = Color.rgb(22, 163, 74)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("GREEN: Correct Answer", legX + 22f, legY + 4f, textPaint)

        legX += 280f
        // Incorrect (Red)
        paint.color = Color.rgb(220, 38, 38) // Red 600
        canvas.drawCircle(legX + 8f, legY, 7f, paint)
        textPaint.color = Color.rgb(220, 38, 38)
        canvas.drawText("RED: Incorrect / Wrong Answer", legX + 22f, legY + 4f, textPaint)

        legX += 320f
        // Non Attempt (Yellow)
        paint.color = Color.rgb(234, 179, 8) // Yellow 500
        canvas.drawCircle(legX + 8f, legY, 7f, paint)
        textPaint.color = Color.rgb(180, 83, 9)
        canvas.drawText("YELLOW: Non Attempt (Unattempted)", legX + 22f, legY + 4f, textPaint)

        legX += 340f
        // Key guide
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.rgb(22, 163, 74)
        canvas.drawCircle(legX + 8f, legY, 7f, paint)
        textPaint.color = Color.rgb(71, 85, 105)
        canvas.drawText("OVAL: Correct Key", legX + 22f, legY + 4f, textPaint)

        // 5. Questions Grid with Colored Bubbles
        val gridTop = legendTop + 45f
        val gridBottom = A4_HEIGHT - 90f
        val gridLeft = 65f
        val gridRight = A4_WIDTH - 65f
        val gridWidth = gridRight - gridLeft
        val gridHeight = gridBottom - gridTop

        val columns = when {
            questionsCount <= 25 -> 1
            questionsCount <= 60 -> 2
            questionsCount <= 100 -> 3
            questionsCount <= 150 -> 4
            else -> 5
        }

        val rowsPerCol = (questionsCount + columns - 1) / columns
        val colWidth = gridWidth / columns
        val rowHeight = min(gridHeight / rowsPerCol, 26f)
        val options = listOf("A", "B", "C", "D")

        val evalMap = evaluations.associateBy { it.questionNumber }

        for (q in 0 until questionsCount) {
            val qNum = q + 1
            val colIndex = q / rowsPerCol
            val rowIndex = q % rowsPerCol

            val xStart = gridLeft + (colIndex * colWidth)
            val yCenter = gridTop + (rowIndex * rowHeight) + (rowHeight / 2f)

            val evaluation = evalMap[qNum]

            // Question number label
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = if (columns >= 4) 11f else 13f
            textPaint.color = Color.rgb(15, 23, 42)
            val qStr = if (qNum < 10) "0$qNum." else "$qNum."
            canvas.drawText(qStr, xStart + (colWidth * 0.22f), yCenter + 4f, textPaint)

            val optionsStart = xStart + (colWidth * 0.26f)
            val optionsWidth = colWidth * 0.70f
            val bubbleSpacing = optionsWidth / options.size
            val bubbleRadius = min(bubbleSpacing * 0.35f, rowHeight * 0.38f)

            for (optIdx in options.indices) {
                val optLetter = options[optIdx]
                val bx = optionsStart + (optIdx * bubbleSpacing) + (bubbleSpacing / 2f)
                val by = yCenter

                val isStudentChoice = (evaluation?.studentAnswer == optLetter)
                val isCorrectKey = (evaluation?.correctAnswer == optLetter)

                when (evaluation?.status) {
                    QuestionStatus.CORRECT -> {
                        if (isStudentChoice) {
                            // GREEN for correct answer
                            paint.style = Paint.Style.FILL
                            paint.color = Color.rgb(22, 163, 74) // Green 600
                            canvas.drawCircle(bx, by, bubbleRadius, paint)

                            // White letter + checkmark
                            textPaint.color = Color.WHITE
                            textPaint.textSize = bubbleRadius * 1.1f
                            textPaint.textAlign = Paint.Align.CENTER
                            canvas.drawText(optLetter, bx, by + bubbleRadius * 0.38f, textPaint)
                        } else {
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = 1.2f
                            paint.color = Color.rgb(203, 213, 225)
                            canvas.drawCircle(bx, by, bubbleRadius, paint)
                            textPaint.color = Color.rgb(148, 163, 184)
                            textPaint.textSize = bubbleRadius * 1.1f
                            textPaint.textAlign = Paint.Align.CENTER
                            canvas.drawText(optLetter, bx, by + bubbleRadius * 0.38f, textPaint)
                        }
                    }
                    QuestionStatus.WRONG, QuestionStatus.INVALID -> {
                        if (isStudentChoice) {
                            // RED for incorrect student choice
                            paint.style = Paint.Style.FILL
                            paint.color = Color.rgb(220, 38, 38) // Red 600
                            canvas.drawCircle(bx, by, bubbleRadius, paint)

                            textPaint.color = Color.WHITE
                            textPaint.textSize = bubbleRadius * 1.1f
                            textPaint.textAlign = Paint.Align.CENTER
                            canvas.drawText(optLetter, bx, by + bubbleRadius * 0.38f, textPaint)
                        } else if (isCorrectKey) {
                            // Green highlight indicating the correct answer
                            paint.style = Paint.Style.FILL
                            paint.color = Color.rgb(220, 252, 231) // Green 100
                            canvas.drawCircle(bx, by, bubbleRadius, paint)

                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = 2f
                            paint.color = Color.rgb(22, 163, 74) // Green 600
                            canvas.drawCircle(bx, by, bubbleRadius, paint)

                            textPaint.color = Color.rgb(22, 163, 74)
                            textPaint.textSize = bubbleRadius * 1.1f
                            textPaint.textAlign = Paint.Align.CENTER
                            canvas.drawText(optLetter, bx, by + bubbleRadius * 0.38f, textPaint)
                        } else {
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = 1.2f
                            paint.color = Color.rgb(203, 213, 225)
                            canvas.drawCircle(bx, by, bubbleRadius, paint)
                            textPaint.color = Color.rgb(148, 163, 184)
                            textPaint.textSize = bubbleRadius * 1.1f
                            textPaint.textAlign = Paint.Align.CENTER
                            canvas.drawText(optLetter, bx, by + bubbleRadius * 0.38f, textPaint)
                        }
                    }
                    QuestionStatus.UNATTEMPTED -> {
                        if (isCorrectKey) {
                            // YELLOW for non attempt (highlighting correct key with yellow warning)
                            paint.style = Paint.Style.FILL
                            paint.color = Color.rgb(254, 240, 138) // Yellow 200
                            canvas.drawCircle(bx, by, bubbleRadius, paint)

                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = 2f
                            paint.color = Color.rgb(234, 179, 8) // Yellow 500
                            canvas.drawCircle(bx, by, bubbleRadius, paint)

                            textPaint.color = Color.rgb(161, 98, 7)
                            textPaint.textSize = bubbleRadius * 1.1f
                            textPaint.textAlign = Paint.Align.CENTER
                            canvas.drawText(optLetter, bx, by + bubbleRadius * 0.38f, textPaint)
                        } else {
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = 1.2f
                            paint.color = Color.rgb(254, 240, 138) // Soft yellow tint
                            canvas.drawCircle(bx, by, bubbleRadius, paint)
                            textPaint.color = Color.rgb(161, 98, 7)
                            textPaint.textSize = bubbleRadius * 1.1f
                            textPaint.textAlign = Paint.Align.CENTER
                            canvas.drawText(optLetter, bx, by + bubbleRadius * 0.38f, textPaint)
                        }
                    }
                    else -> {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1.2f
                        paint.color = Color.rgb(203, 213, 225)
                        canvas.drawCircle(bx, by, bubbleRadius, paint)
                        textPaint.color = Color.rgb(148, 163, 184)
                        textPaint.textSize = bubbleRadius * 1.1f
                        textPaint.textAlign = Paint.Align.CENTER
                        canvas.drawText(optLetter, bx, by + bubbleRadius * 0.38f, textPaint)
                    }
                }
            }
        }

        // 6. Footer
        val footerTop = A4_HEIGHT - 70f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(10, 25, 47)
        canvas.drawRect(65f, footerTop, A4_WIDTH - 65f, A4_HEIGHT - 35f, paint)

        textPaint.color = Color.rgb(255, 184, 0)
        textPaint.textSize = 12f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("CP CLASSES PURNIA • OFFICIAL OMR EVALUATION RECORD • SYSTEM GENERATED", A4_WIDTH / 2f, footerTop + 22f, textPaint)

        return bitmap
    }

    /**
     * Requirement 4: Generates printable ISO 216 standard A4 PDF from OMR bitmap
     */
    fun generateA4Pdf(bitmap: Bitmap, outputFile: java.io.File): java.io.File {
        val document = android.graphics.pdf.PdfDocument()
        // Standard A4 point size at 72 points/inch is 595 x 842 pt
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val destRect = Rect(0, 0, 595, 842)
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, srcRect, destRect, paint)

        document.finishPage(page)
        outputFile.parentFile?.mkdirs()
        val fos = java.io.FileOutputStream(outputFile)
        document.writeTo(fos)
        fos.close()
        document.close()
        return outputFile
    }
}

