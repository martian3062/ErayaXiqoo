package com.evolet.tachyon.twin

import android.content.Context
import android.util.Log
import com.evolet.tachyon.EngineRegistry
import com.evolet.tachyon.agents.AgentBus
import com.evolet.tachyon.agents.AgentEvent
import com.evolet.tachyon.audio.AnswerRecorder
import com.evolet.tachyon.voice.SystemVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

enum class Stage { IDLE, ASKING, RECORDING, TRANSCRIBING, SUMMARISING, REVIEW }
enum class ReviewStatus { PENDING, CONFIRMED, REJECTED }

data class ReviewItem(val proposal: TraitProposal, val status: ReviewStatus = ReviewStatus.PENDING, val finalValue: String? = null)

data class InterviewUi(
    val stage: Stage = Stage.IDLE,
    val questions: List<Question> = emptyList(),
    val sections: Map<Int, String> = emptyMap(),
    val index: Int = 0,
    val followUp: String? = null,
    val answerText: String = "",
    val answered: Int = 0,
    val review: List<ReviewItem> = emptyList(),
    val droppedTraits: Int = 0,
    val error: String? = null,
    val voiceName: String? = null,
) {
    val current get() = questions.getOrNull(index)
    val prompt get() = followUp ?: current?.text
}

/**
 * F17 interview flow (INTEGRATIONSv2.md §7.2): ask → record (VAD) → transcribe → confirm/edit →
 * at most one follow-up → per-section trait proposals in the background → Trait Review.
 * Only ✓/✎ traits reach persona.json; every decision is a TRAIT_DECISION preference pair.
 */
class InterviewController(
    context: Context,
    private val scope: CoroutineScope,
    private val engines: EngineRegistry,
    private val personaStore: PersonaStore,
    private val bus: AgentBus,
) {
    private val bank by lazy { QuestionBank.load(context) }
    private val voice = SystemVoice(context)
    private val recorder = AnswerRecorder()

    private val _ui = MutableStateFlow(InterviewUi())
    val ui: StateFlow<InterviewUi> = _ui.asStateFlow()
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val answers = mutableListOf<Answer>()
    private var pendingMain: String? = null
    private val summaries = mutableListOf<Job>()
    @Volatile private var stopRecording = false
    var speakQuestions = true

    fun start(demo: Boolean) {
        answers.clear(); summaries.clear(); pendingMain = null
        _ui.value = InterviewUi(
            stage = Stage.ASKING,
            questions = bank.plan(demo),
            sections = bank.sections.associate { it.id to it.title },
            voiceName = if (voice.available) voice.name else null,
        )
        speakCurrent()
    }

    fun cancel() {
        voice.stop(); stopRecording = true
        _ui.value = InterviewUi()
    }

    fun onAnswerText(t: String) = _ui.update { it.copy(answerText = t) }

    fun record() {
        if (_ui.value.stage != Stage.ASKING) return
        voice.stop()
        stopRecording = false
        _ui.update { it.copy(stage = Stage.RECORDING, error = null) }
        scope.launch {
            val pcm = recorder.record(onLevel = { _level.value = it }, stop = { stopRecording })
            if (pcm == null) {
                _ui.update { it.copy(stage = Stage.ASKING, error = "Didn't catch that — try again or type your answer.") }
                return@launch
            }
            _ui.update { it.copy(stage = Stage.TRANSCRIBING) }
            val text = runCatching { engines.asr().transcribe(pcm, null).text }.getOrElse { e ->
                _ui.update { it.copy(stage = Stage.ASKING, error = "Transcription failed: ${e.message}") }
                return@launch
            }
            _ui.update { it.copy(stage = Stage.ASKING, answerText = (it.answerText + " " + text).trim()) }
        }
    }

    fun stopRecordingNow() {
        stopRecording = true
    }

    /** "Next": maybe one follow-up, else store the answer and move on (summarising finished sections). */
    fun next() {
        val s = _ui.value
        val q = s.current ?: return
        val text = s.answerText.trim()
        if (text.isEmpty()) {
            _ui.update { it.copy(error = "Answer out loud or type something first (or Skip).") }
            return
        }
        if (s.followUp == null) {
            val fu = InterviewEngine.followUpFor(q, text, alreadyAsked = false)
            if (fu != null) {
                pendingMain = text
                _ui.update { it.copy(followUp = fu, answerText = "", error = null) }
                if (speakQuestions) voice.speak(fu)
                return
            }
            answers += Answer(q, text)
        } else {
            answers += Answer(q, pendingMain.orEmpty(), text)
            pendingMain = null
        }
        advance()
    }

    fun skip() {
        pendingMain?.let { main -> _ui.value.current?.let { answers += Answer(it, main) } }
        pendingMain = null
        advance()
    }

    private fun advance() {
        val s = _ui.value
        val q = s.current ?: return
        val nextQ = s.questions.getOrNull(s.index + 1)
        if (nextQ == null || nextQ.section != q.section) summarise(q.section)
        if (nextQ == null) {
            _ui.update { it.copy(stage = Stage.SUMMARISING, followUp = null, answerText = "", answered = answers.size, error = null) }
            scope.launch {
                summaries.toList().joinAll()
                _ui.update { it.copy(stage = Stage.REVIEW) }
            }
        } else {
            _ui.update { it.copy(index = it.index + 1, followUp = null, answerText = "", answered = answers.size, error = null) }
            speakCurrent()
        }
    }

    private fun summarise(section: Int) {
        val sectionAnswers = answers.filter { it.question.section == section && it.full.isNotBlank() }
        if (sectionAnswers.isEmpty()) return
        summaries += scope.launch {
            runCatching { InterviewEngine.extract(engines.llm(), sectionAnswers) }
                .onSuccess { r -> _ui.update { it.copy(review = it.review + r.proposals.map { p -> ReviewItem(p) }, droppedTraits = it.droppedTraits + r.dropped) } }
                .onFailure { e -> Log.w("Interview", "section $section summary failed", e); _ui.update { it.copy(error = "Couldn't summarise section $section: ${e.message}") } }
        }
    }

    private fun speakCurrent() {
        if (speakQuestions) _ui.value.current?.let { voice.speak(it.text) }
    }

    // --- Trait Review (§7.5) ---

    fun confirm(i: Int, editedValue: String? = null) = decide(i, ReviewStatus.CONFIRMED, editedValue)
    fun reject(i: Int) = decide(i, ReviewStatus.REJECTED, null)

    private fun decide(i: Int, status: ReviewStatus, editedValue: String?) {
        val item = _ui.value.review.getOrNull(i) ?: return
        if (item.status != ReviewStatus.PENDING) return
        val p = item.proposal
        val final = if (status == ReviewStatus.CONFIRMED) (editedValue?.trim()?.takeIf { it.isNotEmpty() } ?: p.value) else null
        if (final != null) {
            val trait = Trait(p.field, final, p.evidence, "interview", System.currentTimeMillis())
            personaStore.savePersona(PersonaMerger.merge(personaStore.persona.value, trait))
        }
        bus.emit(AgentEvent.TraitDecision(p.field, p.evidence, p.value, final))
        _ui.update { s -> s.copy(review = s.review.mapIndexed { j, r -> if (j == i) r.copy(status = status, finalValue = final) else r }) }
    }
}
