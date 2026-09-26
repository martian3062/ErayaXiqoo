package com.evolet.tachyon.eraya

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaValidatorTest {

    private val transcript = """
        Okay, quick sync on the clinic rollout. I'll send you the revised intake form by Friday evening.
        Maybe we should also think about a WhatsApp reminder someday.
        Main kal tak pharmacy ka stock report bhej dunga. मैं कल तक रिपोर्ट भेज दूंगा।
    """.trimIndent()

    private val v = SchemaValidator()

    private fun item(evidence: String, confidence: Double = 0.9) =
        """{"owner":"A","task":"t","deadline_text":"none","evidence":"$evidence","confidence":$confidence}"""

    @Test fun `keeps grounded items and strips prose around the JSON`() {
        val raw = "Sure! ```json\n{\"commitments\":[${item("I'll send you the revised intake form by Friday evening")}]}\n```"
        val r = v.validate(raw, transcript) as Validation.Valid
        assertEquals(1, r.items.size)
    }

    @Test fun `evidence match ignores case, spacing and punctuation`() {
        val raw = """{"commitments":[${item("i'll SEND you the revised   intake form, by friday evening.")}]}"""
        assertEquals(1, (v.validate(raw, transcript) as Validation.Valid).items.size)
    }

    @Test fun `drops hallucinated evidence`() {
        val raw = """{"commitments":[${item("I will call the vendor on Monday")}]}"""
        val r = v.validate(raw, transcript) as Validation.Valid
        assertEquals(0, r.items.size)
        assertEquals(1, r.droppedNoEvidence)
    }

    @Test fun `drops low confidence`() {
        val raw = """{"commitments":[${item("Main kal tak pharmacy ka stock report bhej dunga", 0.3)}]}"""
        val r = v.validate(raw, transcript) as Validation.Valid
        assertEquals(1, r.droppedLowConfidence)
    }

    @Test fun `devanagari evidence matches`() {
        val raw = """{"commitments":[${item("मैं कल तक रिपोर्ट भेज दूंगा")}]}"""
        assertEquals(1, (v.validate(raw, transcript) as Validation.Valid).items.size)
    }

    @Test fun `partial words do not count as evidence`() {
        val raw = """{"commitments":[${item("end you the revised")}]}"""
        assertEquals(0, (v.validate(raw, transcript) as Validation.Valid).items.size)
    }

    @Test fun `repairs bare array and trailing comma`() {
        val raw = "[${item("Main kal tak pharmacy ka stock report bhej dunga")},]"
        assertEquals(1, (v.validate(raw, transcript) as Validation.Valid).items.size)
    }

    @Test fun `garbage is invalid, not a crash`() {
        assertTrue(v.validate("I could not find anything", transcript) is Validation.Invalid)
        assertTrue(v.validate("{\"commitments\": [ {\"owner\": ", transcript) is Validation.Invalid)
    }

    @Test fun `empty list is valid`() {
        assertEquals(0, (v.validate("""{"commitments": []}""", transcript) as Validation.Valid).items.size)
    }

    @Test fun `drops hedged ideas even when the model proposes them`() {
        val raw = """{"commitments":[${item("Maybe we should also think about a WhatsApp reminder someday")},${item("I'll send you the revised intake form by Friday evening")}]}"""
        val r = v.validate(raw, transcript) as Validation.Valid
        assertEquals(1, r.items.size)
        assertEquals(1, r.droppedHypothetical)
    }

    @Test fun `firm promises are not hedged`() {
        assertEquals(false, SchemaValidator.isHedged("I'll book the training room for Monday morning today itself"))
        assertEquals(false, SchemaValidator.isHedged("Main kal tak pharmacy ka stock report bhej dunga"))
        assertEquals(true, SchemaValidator.isHedged("Shayad main kal bhej dunga"))
    }
}
