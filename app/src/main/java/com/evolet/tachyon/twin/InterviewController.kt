package com.evolet.tachyon.twin

import android.content.Context
import android.util.Log
import com.evolet.tachyon.EngineRegistry
import com.evolet.tachyon.agents.AgentBus
import com.evolet.tachyon.agents.AgentEvent
import com.evolet.tachyon.audio.AnswerRecorder
import com.evolet.tachyon.voice.SystemVoice
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

enum class Stage { IDLE, PAUSED, ASKING, RECORDING, TRANSCRIBING, SUMMARISING, REVIEW }
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
 * F17 interview flow: ask -> record -> transcribe -> edit -> optional follow-up ->
 * per-section trait proposals -> explicit Trait Review. Progress is checkpointed privately.
 */
class InterviewController(
    context: Context,
    private val scope: CoroutineScope,
    private val engines: EngineRegistry,
    private val personaStore: PersonaStore,
    private val bus: AgentBus,
    private val voice: SystemVoice,
) {
    private val bank by lazy { QuestionBank.load(context) }
    private val recorder = AnswerRecorder()
    private val draftStore = InterviewDraftStore(context)

    private val _ui = MutableStateFlow(InterviewUi())
    val ui: StateFlow<InterviewUi> = _ui.asStateFlow()
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val answers = mutableListOf<Answer>()
    private var pendingMain: String? = null
    private val summaries = mutableListOf<Job>()
    private val summarizedSections = Collections.synchronizedSet(mutableSetOf<Int>())
    private var draftDemo = false
    private var resumePhase = InterviewDraftPhase.IN_PROGRESS
    private var draftWrite: Job? = null
    @Volatile private var stopRecording = false
    var speakQuestions = true

    init {
        restoreDraft()
    }

    /** Open a new interview only when no in-memory or persisted interview exists. */
    fun prepare(demo: Boolean) {
        if (_ui.value.stage == Stage.IDLE) start(demo)
    }

    fun start(demo: Boolean) {
        draftWrite?.cancel()
        summaries.forEach { it.cancel() }
        answers.clear()
        summaries.clear()
        summarizedSections.clear()
        pendingMain = null
        draftDemo = demo
        resumePhase = InterviewDraftPhase.IN_PROGRESS
        draftStore.clear()
        _ui.value = InterviewUi(
            stage = Stage.ASKING,
            questions = bank.plan(demo),
            sections = bank.sections.associate { it.id to it.title },
            voiceName = if (voice.available) voice.name else null,
        )
        saveDraft()
        speakCurrent()
    }

    fun startOver() = start(draftDemo)

    fun resume() {
        if (_ui.value.stage != Stage.PAUSED) return
        when (resumePhase) {
            InterviewDraftPhase.IN_PROGRESS -> {
                _ui.update { it.copy(stage = Stage.ASKING, error = null) }
                resumeCompletedSummaries()
                saveDraft()
                speakCurrent()
            }
            InterviewDraftPhase.SUMMARISING -> finishSummaries()
            InterviewDraftPhase.REVIEW -> {
                _ui.update { it.copy(stage = Stage.REVIEW, error = null) }
                saveDraft(InterviewDraftPhase.REVIEW)
            }
        }
    }

    /** Stop means pause: answers and review decisions remain in private app storage. */
    fun cancel() {
        voice.stop()
        stopRecording = true
        val stage = _ui.value.stage
        if (stage == Stage.IDLE) return
        resumePhase = when (stage) {
            Stage.SUMMARISING -> InterviewDraftPhase.SUMMARISING
            Stage.REVIEW -> InterviewDraftPhase.REVIEW
            Stage.PAUSED -> resumePhase
            else -> InterviewDraftPhase.IN_PROGRESS
        }
        summaries.forEach { it.cancel() }
        summaries.clear()
        _ui.update { it.copy(stage = Stage.PAUSED, error = null) }
        saveDraft(resumePhase)
    }

    /** Review is complete only when the user explicitly taps Done. */
    fun finish() {
        voice.stop()
        stopRecording = true
        draftWrite?.cancel()
        summaries.forEach { it.cancel() }
        summaries.clear()
        draftStore.clear()
        _ui.value = InterviewUi()
    }

    fun onAnswerText(text: String) {
        _ui.update { it.copy(answerText = text) }
        scheduleDraftSave()
    }

    fun record() {
        if (_ui.value.stage != Stage.ASKING) return
        voice.stop()
        stopRecording = false
        _ui.update { it.copy(stage = Stage.RECORDING, error = null) }
        saveDraft()
        scope.launch {
            val pcm = recorder.record(onLevel = { _level.value = it }, stop = { stopRecording })
            if (pcm == null) {
                _ui.update { it.copy(stage = Stage.ASKING, error = "Didn't catch that - try again or type your answer.") }
                saveDraft()
                return@launch
            }
            _ui.update { it.copy(stage = Stage.TRANSCRIBING) }
            val text = runCatching { engines.asr().transcribe(pcm, null).text }.getOrElse { error ->
                _ui.update { it.copy(stage = Stage.ASKING, error = "Transcription failed: ${error.message}") }
                saveDraft()
                return@launch
            }
            _ui.update { it.copy(stage = Stage.ASKING, answerText = (it.answerText + " " + text).trim()) }
            saveDraft()
        }
    }

    fun stopRecordingNow() {
        stopRecording = true
    }

    /** Next asks at most one follow-up, then stores the answer and moves forward. */
    fun next() {
        val state = _ui.value
        val question = state.current ?: return
        val text = state.answerText.trim()
        if (text.isEmpty()) {
            _ui.update { it.copy(error = "Answer out loud or type something first (or Skip).") }
            return
        }
        if (state.followUp == null) {
            val followUp = InterviewEngine.followUpFor(question, text, alreadyAsked = false)
            if (followUp != null) {
                pendingMain = text
                _ui.update { it.copy(followUp = followUp, answerText = "", error = null) }
                saveDraft()
                if (speakQuestions) voice.speak(followUp)
                return
            }
            answers += Answer(question, text)
        } else {
            answers += Answer(question, pendingMain.orEmpty(), text)
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
        val state = _ui.value
        val question = state.current ?: return
        val nextQuestion = state.questions.getOrNull(state.index + 1)
        if (nextQuestion == null || nextQuestion.section != question.section) summarise(question.section)
        if (nextQuestion == null) {
            _ui.update {
                it.copy(
                    stage = Stage.SUMMARISING,
                    followUp = null,
                    answerText = "",
                    answered = answers.size,
                    error = null,
                )
            }
            saveDraft(InterviewDraftPhase.SUMMARISING)
            waitForSummaries()
        } else {
            _ui.update {
                it.copy(
                    index = it.index + 1,
                    followUp = null,
                    answerText = "",
                    answered = answers.size,
                    error = null,
                )
            }
            saveDraft()
            speakCurrent()
        }
    }

    private fun summarise(section: Int) {
        if (section in summarizedSections) return
        val sectionAnswers = answers.filter { it.question.section == section && it.full.isNotBlank() }
        if (sectionAnswers.isEmpty()) return
        summaries += scope.launch {
            runCatching { InterviewEngine.extract(engines.llm(), sectionAnswers) }
                .onSuccess { result ->
                    summarizedSections += section
                    _ui.update { state ->
                        val additions = result.proposals
                            .filterNot { proposal ->
                                state.review.any {
                                    it.proposal.field == proposal.field &&
                                        it.proposal.value == proposal.value &&
                                        it.proposal.evidence == proposal.evidence
                                }
                            }
                            .map(::ReviewItem)
                        state.copy(
                            review = state.review + additions,
                            droppedTraits = state.droppedTraits + result.dropped,
                        )
                    }
                    saveDraft()
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    Log.w("Interview", "section $section summary failed", error)
                    _ui.update { it.copy(error = "Couldn't summarise section $section: ${error.message}") }
                    saveDraft()
                }
        }
    }

    private fun speakCurrent() {
        if (speakQuestions) _ui.value.current?.let { voice.speak(it.text) }
    }

    fun confirm(index: Int, editedValue: String? = null) = decide(index, ReviewStatus.CONFIRMED, editedValue)
    fun reject(index: Int) = decide(index, ReviewStatus.REJECTED, null)

    private fun decide(index: Int, status: ReviewStatus, editedValue: String?) {
        val item = _ui.value.review.getOrNull(index) ?: return
        if (item.status != ReviewStatus.PENDING) return
        val proposal = item.proposal
        val finalValue = if (status == ReviewStatus.CONFIRMED) {
            editedValue?.trim()?.takeIf { it.isNotEmpty() } ?: proposal.value
        } else {
            null
        }
        if (finalValue != null) {
            val trait = Trait(proposal.field, finalValue, proposal.evidence, "interview", System.currentTimeMillis())
            personaStore.savePersona(PersonaMerger.merge(personaStore.persona.value, trait))
        }
        bus.emit(AgentEvent.TraitDecision(proposal.field, proposal.evidence, proposal.value, finalValue))
        _ui.update { state ->
            state.copy(
                review = state.review.mapIndexed { itemIndex, review ->
                    if (itemIndex == index) review.copy(status = status, finalValue = finalValue) else review
                },
            )
        }
        saveDraft(InterviewDraftPhase.REVIEW)
    }

    private fun restoreDraft() {
        val draft = draftStore.load() ?: return
        val byId = bank.questions.associateBy { it.id }
        val questions = draft.questionIds.mapNotNull(byId::get)
        if (questions.isEmpty() || draft.index !in questions.indices) {
            draftStore.clear()
            return
        }
        answers += draft.answers.mapNotNull { saved ->
            byId[saved.questionId]?.let { Answer(it, saved.text, saved.followUpText) }
        }
        summarizedSections += draft.summarizedSections
        pendingMain = draft.pendingMain
        draftDemo = draft.demo
        resumePhase = draft.phase
        _ui.value = InterviewUi(
            stage = Stage.PAUSED,
            questions = questions,
            sections = bank.sections.associate { it.id to it.title },
            index = draft.index,
            followUp = draft.followUp,
            answerText = draft.answerText,
            answered = draft.answered,
            review = draft.review.map {
                ReviewItem(
                    TraitProposal(it.field, it.value, it.evidence, it.confidence),
                    it.status,
                    it.finalValue,
                )
            },
            droppedTraits = draft.droppedTraits,
            voiceName = if (voice.available) voice.name else null,
        )
    }

    /** Restart background summaries for completed sections that were interrupted by process death. */
    private fun resumeCompletedSummaries() {
        val currentSection = _ui.value.current?.section ?: Int.MAX_VALUE
        answers.map { it.question.section }.distinct()
            .filter { it < currentSection && it !in summarizedSections }
            .forEach(::summarise)
    }

    private fun finishSummaries() {
        _ui.update { it.copy(stage = Stage.SUMMARISING, error = null) }
        answers.map { it.question.section }.distinct()
            .filterNot(summarizedSections::contains)
            .forEach(::summarise)
        saveDraft(InterviewDraftPhase.SUMMARISING)
        waitForSummaries()
    }

    private fun waitForSummaries() {
        scope.launch {
            summaries.toList().joinAll()
            if (_ui.value.stage == Stage.SUMMARISING) {
                resumePhase = InterviewDraftPhase.REVIEW
                _ui.update { it.copy(stage = Stage.REVIEW) }
                saveDraft(InterviewDraftPhase.REVIEW)
            }
        }
    }

    private fun scheduleDraftSave() {
        val snapshot = snapshot()
        draftWrite?.cancel()
        draftWrite = scope.launch(Dispatchers.IO) {
            delay(250)
            runCatching { draftStore.save(snapshot) }
                .onFailure { Log.w("Interview", "draft save failed", it) }
        }
    }

    private fun saveDraft(phase: InterviewDraftPhase = currentPhase()) {
        resumePhase = phase
        val snapshot = snapshot(phase)
        draftWrite?.cancel()
        draftWrite = scope.launch(Dispatchers.IO) {
            runCatching { draftStore.save(snapshot) }
                .onFailure { Log.w("Interview", "draft save failed", it) }
        }
    }

    private fun currentPhase(): InterviewDraftPhase = when (_ui.value.stage) {
        Stage.PAUSED -> resumePhase
        Stage.SUMMARISING -> InterviewDraftPhase.SUMMARISING
        Stage.REVIEW -> InterviewDraftPhase.REVIEW
        else -> InterviewDraftPhase.IN_PROGRESS
    }

    private fun snapshot(phase: InterviewDraftPhase = resumePhase): InterviewDraft {
        val state = _ui.value
        return InterviewDraft(
            demo = draftDemo,
            questionIds = state.questions.map { it.id },
            index = state.index.coerceIn(0, (state.questions.size - 1).coerceAtLeast(0)),
            followUp = state.followUp,
            pendingMain = pendingMain,
            answerText = state.answerText,
            answers = answers.map { InterviewDraftAnswer(it.question.id, it.text, it.followUpText) },
            review = state.review.map {
                InterviewDraftReview(
                    it.proposal.field,
                    it.proposal.value,
                    it.proposal.evidence,
                    it.proposal.confidence,
                    it.status,
                    it.finalValue,
                )
            },
            summarizedSections = summarizedSections.toSet(),
            droppedTraits = state.droppedTraits,
            answered = state.answered,
            phase = phase,
        )
    }
}
