package com.example.omr

import com.example.data.model.QuestionStatus
import com.example.data.model.TopicDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmrScanTest {

    @Test
    fun testBubbleCvProcessorRowEvaluation() {
        val options = listOf("A", "B", "C", "D")

        // Case 1: Clean single mark on option B
        val evalsCleanB = listOf(
            OmrBubbleCvProcessor.BubbleEvaluation(10f, 10f, 6f, 0.02f, 0.03f, 220f, 214f, OmrBubbleCvProcessor.BubbleFillState.EMPTY, 0.95f, false),
            OmrBubbleCvProcessor.BubbleEvaluation(30f, 10f, 6f, 0.82f, 0.74f, 220f, 57f, OmrBubbleCvProcessor.BubbleFillState.FILLED, 0.98f, true),
            OmrBubbleCvProcessor.BubbleEvaluation(50f, 10f, 6f, 0.01f, 0.02f, 220f, 216f, OmrBubbleCvProcessor.BubbleFillState.EMPTY, 0.96f, false),
            OmrBubbleCvProcessor.BubbleEvaluation(70f, 10f, 6f, 0.03f, 0.04f, 220f, 211f, OmrBubbleCvProcessor.BubbleFillState.EMPTY, 0.94f, false)
        )
        val rowCleanB = OmrBubbleCvProcessor.evaluateQuestionRow(0, evalsCleanB, options)
        assertEquals("B", rowCleanB.selectedOption)
        assertTrue("Dominant margin should be high", rowCleanB.dominantMargin > 0.60f)

        // Case 2: Completely unattempted question
        val evalsEmpty = listOf(
            OmrBubbleCvProcessor.BubbleEvaluation(10f, 10f, 6f, 0.01f, 0.01f, 220f, 218f, OmrBubbleCvProcessor.BubbleFillState.EMPTY, 0.98f, false),
            OmrBubbleCvProcessor.BubbleEvaluation(30f, 10f, 6f, 0.02f, 0.02f, 220f, 215f, OmrBubbleCvProcessor.BubbleFillState.EMPTY, 0.97f, false),
            OmrBubbleCvProcessor.BubbleEvaluation(50f, 10f, 6f, 0.01f, 0.01f, 220f, 217f, OmrBubbleCvProcessor.BubbleFillState.EMPTY, 0.98f, false),
            OmrBubbleCvProcessor.BubbleEvaluation(70f, 10f, 6f, 0.03f, 0.02f, 220f, 215f, OmrBubbleCvProcessor.BubbleFillState.EMPTY, 0.96f, false)
        )
        val rowEmpty = OmrBubbleCvProcessor.evaluateQuestionRow(1, evalsEmpty, options)
        assertEquals("-", rowEmpty.selectedOption)

        // Case 3: Multiple marked bubbles (both A and C filled)
        val evalsMultiple = listOf(
            OmrBubbleCvProcessor.BubbleEvaluation(10f, 10f, 6f, 0.75f, 0.68f, 220f, 70f, OmrBubbleCvProcessor.BubbleFillState.FILLED, 0.92f, true),
            OmrBubbleCvProcessor.BubbleEvaluation(30f, 10f, 6f, 0.02f, 0.02f, 220f, 215f, OmrBubbleCvProcessor.BubbleFillState.EMPTY, 0.97f, false),
            OmrBubbleCvProcessor.BubbleEvaluation(50f, 10f, 6f, 0.78f, 0.70f, 220f, 66f, OmrBubbleCvProcessor.BubbleFillState.FILLED, 0.94f, true),
            OmrBubbleCvProcessor.BubbleEvaluation(70f, 10f, 6f, 0.03f, 0.02f, 220f, 215f, OmrBubbleCvProcessor.BubbleFillState.EMPTY, 0.96f, false)
        )
        val rowMultiple = OmrBubbleCvProcessor.evaluateQuestionRow(2, evalsMultiple, options)
        assertEquals("*", rowMultiple.selectedOption)
    }

    @Test
    fun testOmrVisionEngineEvaluationMarksAndScoring() {
        val studentAnswers = listOf("A", "B", "C", "D", "-", "*", "A", "B", "C", "D")
        val answerKey = listOf("A", "B", "C", "D", "A", "B", "B", "A", "C", "D")

        val evaluations = OmrVisionEngine.evaluate(
            markedAnswers = studentAnswers,
            answerKey = answerKey,
            marksPerCorrect = 4.0f,
            negativeMarksPerWrong = 1.0f,
            marksUnattempted = 0.0f
        )

        assertEquals(10, evaluations.size)

        // Questions 1-4: Correct (A=A, B=B, C=C, D=D)
        for (i in 0..3) {
            assertEquals("Q${i + 1} should be CORRECT", QuestionStatus.CORRECT, evaluations[i].status)
            assertEquals("Q${i + 1} should award 4.0 marks", 4.0f, evaluations[i].marksAwarded, 0.001f)
        }

        // Question 5: Unattempted
        assertEquals("Q5 should be UNATTEMPTED", QuestionStatus.UNATTEMPTED, evaluations[4].status)
        assertEquals("Q5 should award 0 marks", 0.0f, evaluations[4].marksAwarded, 0.001f)

        // Question 6: Multiple marked (*) -> invalid/penalty
        assertEquals("Q6 should be INVALID", QuestionStatus.INVALID, evaluations[5].status)
        assertEquals("Q6 should penalize -1.0 mark", -1.0f, evaluations[5].marksAwarded, 0.001f)

        // Question 7: Student gave A, key was B -> WRONG
        assertEquals("Q7 should be WRONG", QuestionStatus.WRONG, evaluations[6].status)
        assertEquals("Q7 should penalize -1.0 mark", -1.0f, evaluations[6].marksAwarded, 0.001f)

        // Questions 9-10: Correct (C=C, D=D)
        assertEquals(QuestionStatus.CORRECT, evaluations[8].status)
        assertEquals(QuestionStatus.CORRECT, evaluations[9].status)

        // Total correct: 6 * 4 = 24
        // Total wrong/invalid: 3 * (-1) = -3
        // Total unattempted: 1 * 0 = 0
        // Expected total score = 21.0
        val totalMarks = evaluations.sumOf { it.marksAwarded.toDouble() }
        assertEquals("Total score must equal 21.0", 21.0, totalMarks, 0.001)
    }

    @Test
    fun testEvaluationWithOptionalQuestionTopicLimits() {
        // Test optional question capping (e.g. attempt any 5 of 10)
        val studentAnswers = (1..10).map { "A" }
        val answerKey = (1..10).map { "A" }

        val topic = TopicDefinition(
            name = "Section B (Optional)",
            startQuestion = 1,
            endQuestion = 10,
            maxAttempts = 5
        )

        val evals = OmrVisionEngine.evaluate(
            markedAnswers = studentAnswers,
            answerKey = answerKey,
            marksPerCorrect = 4.0f,
            negativeMarksPerWrong = 1.0f,
            topics = listOf(topic)
        )

        val correctCount = evals.count { it.status == QuestionStatus.CORRECT }
        val excludedCount = evals.count { it.status == QuestionStatus.EXCLUDED_OPTIONAL }

        assertEquals("First 5 attempts should be evaluated", 5, correctCount)
        assertEquals("Remaining 5 attempts should be EXCLUDED_OPTIONAL", 5, excludedCount)
    }

    @Test
    fun testCornerAlignmentRequiresAllFourAlignedAndSteadyBeforeReady() {
        val tracker = CornerAlignmentTracker(requiredSteadyFrames = 5, maxCornerJitter = 0.015f)
        val idealQuad = NormQuad(
            tl = NormPoint(0.10f, 0.12f),
            tr = NormPoint(0.90f, 0.12f),
            bl = NormPoint(0.10f, 0.88f),
            br = NormPoint(0.90f, 0.88f)
        )

        // 1. Fewer than 4 corners detected -> never ready
        val onlyThree = tracker.evaluate(matchedCornersCount = 3, isFiducialsFound = true, quad = idealQuad)
        org.junit.Assert.assertFalse(onlyThree.isAllFourMatched)
        org.junit.Assert.assertFalse(onlyThree.isSteadyAndReady)

        // 2. All 4 corners clipped outside view margin -> not within camera view
        val clippedQuad = idealQuad.copy(tl = NormPoint(0.005f, 0.12f))
        val clipped = tracker.evaluate(matchedCornersCount = 4, isFiducialsFound = true, quad = clippedQuad)
        org.junit.Assert.assertFalse(clipped.isWithinCameraView)
        org.junit.Assert.assertFalse(clipped.isSteadyAndReady)

        // 3. Skewed trapezoid (>12% top/bottom width asymmetry) -> not aligned
        val skewedQuad = NormQuad(
            tl = NormPoint(0.25f, 0.12f),
            tr = NormPoint(0.75f, 0.12f),
            bl = NormPoint(0.10f, 0.88f),
            br = NormPoint(0.90f, 0.88f)
        )
        val skewed = tracker.evaluate(matchedCornersCount = 4, isFiducialsFound = true, quad = skewedQuad)
        org.junit.Assert.assertFalse(skewed.isGeometryAligned)
        org.junit.Assert.assertFalse(skewed.isSteadyAndReady)

        // 4. Moving sheet between consecutive frames resets steady streak to 1
        tracker.evaluate(4, true, idealQuad)
        val shiftedQuad = NormQuad(
            tl = NormPoint(0.14f, 0.12f),
            tr = NormPoint(0.94f, 0.12f),
            bl = NormPoint(0.14f, 0.88f),
            br = NormPoint(0.94f, 0.88f)
        )
        val moved = tracker.evaluate(4, true, shiftedQuad)
        org.junit.Assert.assertTrue(moved.isPerfectlyAligned)
        assertEquals(1, moved.steadyFrameStreak)
        org.junit.Assert.assertFalse(moved.isSteadyAndReady)

        // 5. Holding steady with <= 1.5% jitter for 5 consecutive frames unlocks auto-capture
        tracker.reset()
        var lastResult = tracker.evaluate(4, true, idealQuad)
        assertEquals(1, lastResult.steadyFrameStreak)
        org.junit.Assert.assertFalse(lastResult.isSteadyAndReady)

        for (step in 2..4) {
            val jittered = NormQuad(
                tl = NormPoint(0.10f + step * 0.001f, 0.12f),
                tr = NormPoint(0.90f + step * 0.001f, 0.12f),
                bl = NormPoint(0.10f + step * 0.001f, 0.88f),
                br = NormPoint(0.90f + step * 0.001f, 0.88f)
            )
            lastResult = tracker.evaluate(4, true, jittered)
            assertEquals(step, lastResult.steadyFrameStreak)
            org.junit.Assert.assertFalse("Should not auto-capture before 5 steady frames", lastResult.isSteadyAndReady)
        }

        lastResult = tracker.evaluate(4, true, idealQuad)
        assertEquals(5, lastResult.steadyFrameStreak)
        org.junit.Assert.assertTrue("Should trigger auto-capture once 4 corners are aligned and steady for 5 frames", lastResult.isSteadyAndReady)
    }

    @Test
    fun testSheetCornersPerCornerLockFlagsDefaultAndExplicit() {
        val corners = OmrVisionEngine.SheetCorners(
            topLeft = android.graphics.PointF(10f, 10f),
            topRight = android.graphics.PointF(100f, 10f),
            bottomLeft = android.graphics.PointF(10f, 140f),
            bottomRight = android.graphics.PointF(100f, 140f),
            isFiducialsFound = true,
            matchedCornersCount = 3,
            hasTL = true,
            hasTR = true,
            hasBL = true,
            hasBR = false
        )
        assertTrue(corners.hasTL)
        assertTrue(corners.hasTR)
        assertTrue(corners.hasBL)
        org.junit.Assert.assertFalse(corners.hasBR)
    }

    @Test
    fun testAlignmentProgressVisualFeedbackAndReset() {
        val tracker = CornerAlignmentTracker(requiredSteadyFrames = 5, maxCornerJitter = 0.020f)
        val alignedQuad = NormQuad(
            tl = NormPoint(0.10f, 0.14f),
            tr = NormPoint(0.90f, 0.14f),
            bl = NormPoint(0.10f, 0.86f),
            br = NormPoint(0.90f, 0.86f)
        )

        // Partial corners show partial visual progress
        val twoCorners = tracker.evaluate(matchedCornersCount = 2, isFiducialsFound = true, quad = alignedQuad)
        org.junit.Assert.assertFalse(twoCorners.isPerfectlyAligned)
        assertTrue(twoCorners.alignmentProgress > 0f && twoCorners.alignmentProgress < 0.30f)

        // Monotonic progress increase across steady frames until 1.0f lock
        var prevProgress = 0f
        for (i in 1..5) {
            val res = tracker.evaluate(matchedCornersCount = 4, isFiducialsFound = true, quad = alignedQuad)
            assertTrue("Progress should increase or reach 1.0", res.alignmentProgress >= prevProgress)
            prevProgress = res.alignmentProgress
            if (i < 5) {
                org.junit.Assert.assertFalse(res.isSteadyAndReady)
            } else {
                assertTrue(res.isSteadyAndReady)
                assertEquals(1.0f, res.alignmentProgress, 0.001f)
            }
        }

        // Reset clears streak for next sheet
        tracker.reset()
        val postReset = tracker.evaluate(matchedCornersCount = 4, isFiducialsFound = true, quad = alignedQuad)
        assertEquals(1, postReset.steadyFrameStreak)
        org.junit.Assert.assertFalse(postReset.isSteadyAndReady)
    }
}
