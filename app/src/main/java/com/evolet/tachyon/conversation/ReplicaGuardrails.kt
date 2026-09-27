package com.evolet.tachyon.conversation

/**
 * Small, deterministic safety boundary around the local conversational model.
 *
 * The prompt remains the first line of defence, but these checks do not depend on the model
 * following instructions. They deliberately cover the two risks specific to ERAYA: dumping the
 * private twin context and claiming an external action happened without the owner's confirmation.
 */
object ReplicaGuardrails {
    private val privateDumpRequest = Regex(
        pattern = """\b(show|reveal|print|repeat|dump|list|give)\b.{0,56}\b(system prompt|hidden instructions?|private (owner )?context|stored profile|persona(?:\.json)?)\b""",
        option = RegexOption.IGNORE_CASE,
    )

    private val completedActionClaim = Regex(
        pattern = """\bI(?:'ve| have)?\s+(?:already\s+)?(?:sent|emailed|called|booked|bought|paid|transferred|deleted|changed|scheduled|posted|uploaded|downloaded|saved|added)\b""",
        option = RegexOption.IGNORE_CASE,
    )

    /** Stops bulk disclosure through chat; the owner can review confirmed items in You > My profile. */
    fun preflight(userText: String): String? =
        if (privateDumpRequest.containsMatchIn(userText)) {
            "I can't dump hidden instructions or your private profile into chat. You can review each confirmed item in You, then My profile."
        } else {
            null
        }

    /** Corrects impossible action claims before they reach the UI or text-to-speech engine. */
    fun guardReply(reply: String): String =
        if (completedActionClaim.containsMatchIn(reply)) {
            "I haven't performed that action. I can help prepare it, but ERAYA requires your explicit confirmation before anything is saved, shared, scheduled, or changed."
        } else {
            reply
        }
}
