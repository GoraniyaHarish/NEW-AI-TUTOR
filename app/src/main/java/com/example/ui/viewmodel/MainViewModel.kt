package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AIRouter
import com.example.ai.cloud.CloudAIService
import com.example.ai.local.LocalAIService
import com.example.ai.retrieval.LocalRetriever
import com.example.core.network.NetworkMonitor
import com.example.core.storage.DocumentTextProcessor
import com.example.core.storage.ExtractedChunk
import com.example.core.storage.ExtractedPage
import com.example.core.storage.IngestionResult
import com.example.core.storage.PdfTextExtractor
import com.example.data.local.database.LearnMateDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CourseEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.LearnerSkillEntity
import com.example.data.local.entity.LearningPlanEntity
import com.example.data.local.entity.LearningPlanItemEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRelationEntity
import com.example.data.repository.LearnMateRepository
import com.example.learning.assessment.QuizEvaluator
import com.example.learning.assessment.QuizEvaluationResult
import com.example.learning.mastery.MasteryCalculator
import com.example.learning.personalization.LearningDiagnostics
import com.example.learning.personalization.PersonalizationEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SelectedFileItem(
    val name: String,
    val type: String,
    val size: String,
    val uri: Uri? = null,
    val customText: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LearnMateDatabase.getInstance(application)
    val networkMonitor = NetworkMonitor(application)
    private val localAI = LocalAIService()
    private val cloudAI = CloudAIService()
    private val aiRouter = AIRouter(localAI, cloudAI, networkMonitor)
    private val retriever = LocalRetriever()
    val repository = LearnMateRepository(database, aiRouter, retriever)
    private val pdfExtractor = PdfTextExtractor(application)
    val modelManager = com.example.ai.local.OnDeviceModelManager(application)

    val modelDownloadState = modelManager.downloadState
    val isNeuralEngineEnabled = modelManager.isNeuralEngineEnabled

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    val isSimulatedOffline: StateFlow<Boolean> = networkMonitor.isOfflineSimulated

    val allCourses: StateFlow<List<CourseEntity>> = repository.getAllCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCourseId: StateFlow<Long?> = repository.activeCourseId

    val activeCourse: StateFlow<CourseEntity?> = activeCourseId.flatMapLatest { id ->
        if (id == null) flowOf(null) else flowOf(repository.getCourse(id))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val documents: StateFlow<List<DocumentEntity>> = activeCourseId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getDocuments(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val skills: StateFlow<List<SkillEntity>> = activeCourseId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getSkills(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val relations: StateFlow<List<SkillRelationEntity>> = activeCourseId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getRelations(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val learnerSkills: StateFlow<List<LearnerSkillEntity>> = activeCourseId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getLearnerSkills(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questions: StateFlow<List<QuestionEntity>> = activeCourseId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getQuestions(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestPlan: StateFlow<LearningPlanEntity?> = activeCourseId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.getLatestPlan(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val planItems: StateFlow<List<LearningPlanItemEntity>> = latestPlan.flatMapLatest { plan ->
        if (plan == null) flowOf(emptyList()) else repository.getPlanItems(plan.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatMessages: StateFlow<List<ChatMessageEntity>> = activeCourseId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getChatMessages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reactive Learning Diagnostics
    val diagnostics: StateFlow<LearningDiagnostics?> = combine(
        skills, relations, learnerSkills
    ) { sList, rList, lList ->
        if (sList.isEmpty()) null
        else PersonalizationEngine.analyzeLearner(sList, rList, lList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Processing UI State
    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _currentStageIndex = MutableStateFlow(0)
    val currentStageIndex: StateFlow<Int> = _currentStageIndex.asStateFlow()

    // Tutor UI State
    private val _isTutorThinking = MutableStateFlow(false)
    val isTutorThinking: StateFlow<Boolean> = _isTutorThinking.asStateFlow()

    // Diagnostic Assessment in progress
    private val _diagnosticAnswers = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val diagnosticAnswers: StateFlow<Map<Long, Int>> = _diagnosticAnswers.asStateFlow()

    private val _diagnosticHintsUsed = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val diagnosticHintsUsed: StateFlow<Map<Long, Boolean>> = _diagnosticHintsUsed.asStateFlow()

    private val _lastQuizEvaluation = MutableStateFlow<QuizEvaluationResult?>(null)
    val lastQuizEvaluation: StateFlow<QuizEvaluationResult?> = _lastQuizEvaluation.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureInitialData()
        }
    }

    fun selectCourse(courseId: Long) {
        repository.setActiveCourse(courseId)
    }

    fun toggleOfflineSimulation() {
        networkMonitor.toggleOfflineSimulation()
    }

    fun resetToDemoCourse(onFinished: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.resetToDemoCourse()
            onFinished(id)
        }
    }

    fun createCourseWithFiles(
        title: String,
        description: String,
        files: List<SelectedFileItem>,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val courseId = repository.createCourse(title, description)

            for (file in files) {
                var extractedChunks: List<ExtractedChunk> = emptyList()
                var pageCount = 1

                when {
                    file.uri != null -> {
                        when (val result = pdfExtractor.extractTextFromUri(file.uri, file.name)) {
                            is IngestionResult.Success -> {
                                extractedChunks = result.chunks
                                pageCount = result.pageCount
                            }
                            is IngestionResult.Failure -> {
                                extractedChunks = emptyList()
                                pageCount = 1
                            }
                        }
                    }
                    file.customText != null -> {
                        val normalized = DocumentTextProcessor.normalizeText(file.customText)
                        if (normalized.isNotBlank()) {
                            extractedChunks = DocumentTextProcessor.chunkPages(listOf(ExtractedPage(pageNumber = 1, text = normalized)))
                            pageCount = 1
                        }
                    }
                }

                repository.addDocument(
                    courseId = courseId,
                    fileName = file.name,
                    fileType = file.type,
                    fileSize = file.size,
                    extractedChunks = extractedChunks,
                    pageCount = pageCount
                )
            }

            onCreated(courseId)
        }
    }

    fun runProcessingStages(courseId: Long, onCompleted: () -> Unit) {
        viewModelScope.launch {
            _processingProgress.value = 0f
            _currentStageIndex.value = 0

            val totalStages = 7
            for (i in 0 until totalStages) {
                _currentStageIndex.value = i
                _processingProgress.value = (i + 1).toFloat() / totalStages.toFloat()
                delay(400) // realistic smooth progress for hackathon presentation
            }

            repository.processMaterialAndBuildSkillMap(courseId)
            delay(300)
            onCompleted()
        }
    }

    fun answerDiagnosticQuestion(questionId: Long, optionIndex: Int) {
        _diagnosticAnswers.value = _diagnosticAnswers.value + (questionId to optionIndex)
    }

    fun useDiagnosticHint(questionId: Long) {
        _diagnosticHintsUsed.value = _diagnosticHintsUsed.value + (questionId to true)
    }

    fun submitDiagnosticAssessment(courseId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            val qs = database.questionDao().getQuestionsSync(courseId)
            val answers = _diagnosticAnswers.value
            val hints = _diagnosticHintsUsed.value

            // Group questions by skill and update learner skills
            val questionsBySkill = qs.groupBy { it.skillId }
            for ((skillId, skillQuestions) in questionsBySkill) {
                var correct = 0
                var hintsCount = 0
                for (q in skillQuestions) {
                    if (answers[q.id] == q.correctAnswerIndex) correct++
                    if (hints[q.id] == true) hintsCount++
                }

                val current = database.learnerSkillDao().getLearnerSkill(skillId)
                val prevMastery = current?.masteryScore ?: 0
                val result = MasteryCalculator.calculateUpdatedMastery(
                    previousMastery = prevMastery,
                    totalQuestions = skillQuestions.size,
                    correctAnswers = correct,
                    hintsUsed = hintsCount
                )

                database.learnerSkillDao().upsertLearnerSkill(
                    LearnerSkillEntity(
                        skillId = skillId,
                        courseId = courseId,
                        masteryScore = result.newMastery,
                        confidence = 0.85f,
                        attempts = (current?.attempts ?: 0) + 1,
                        correctAnswers = (current?.correctAnswers ?: 0) + correct,
                        incorrectAnswers = (current?.incorrectAnswers ?: 0) + (skillQuestions.size - correct),
                        hintsUsed = (current?.hintsUsed ?: 0) + hintsCount,
                        lastPracticed = System.currentTimeMillis()
                    )
                )
            }

            repository.refreshLearningPlan(courseId)
            _diagnosticAnswers.value = emptyMap()
            _diagnosticHintsUsed.value = emptyMap()
            onDone()
        }
    }

    fun submitPracticeQuiz(
        skillId: Long,
        courseId: Long,
        questions: List<QuestionEntity>,
        userAnswers: Map<Long, Int>,
        hintsUsedMap: Map<Long, Boolean>,
        onResultReady: (Int, Int, Int, Int) -> Unit
    ) {
        viewModelScope.launch {
            val skill = database.skillDao().getSkillById(skillId) ?: return@launch
            val current = database.learnerSkillDao().getLearnerSkill(skillId)
            val prevMastery = current?.masteryScore ?: 0

            val eval = QuizEvaluator.evaluateQuiz(
                questions = questions,
                userAnswers = userAnswers,
                hintsUsedMap = hintsUsedMap,
                previousMastery = prevMastery,
                currentStreak = current?.streak ?: 0
            )
            _lastQuizEvaluation.value = eval

            // Update LearnerSkill in DB
            database.learnerSkillDao().upsertLearnerSkill(
                LearnerSkillEntity(
                    skillId = skillId,
                    courseId = courseId,
                    masteryScore = eval.masteryResult.newMastery,
                    confidence = 0.90f,
                    attempts = (current?.attempts ?: 0) + 1,
                    correctAnswers = (current?.correctAnswers ?: 0) + eval.correctCount,
                    incorrectAnswers = (current?.incorrectAnswers ?: 0) + eval.incorrectCount,
                    hintsUsed = (current?.hintsUsed ?: 0) + eval.hintsUsedCount,
                    lastPracticed = System.currentTimeMillis(),
                    streak = eval.masteryResult.streak
                )
            )

            // Record Quiz Attempt
            repository.recordQuizAttempt(
                QuizAttemptEntity(
                    courseId = courseId,
                    skillId = skillId,
                    skillName = skill.name,
                    score = eval.correctCount,
                    totalQuestions = eval.totalQuestions,
                    beforeMastery = prevMastery,
                    afterMastery = eval.masteryResult.newMastery,
                    summaryText = eval.masteryResult.whatChangedSummary,
                    hintsUsedCount = eval.hintsUsedCount
                )
            )

            repository.refreshLearningPlan(courseId)
            onResultReady(
                eval.correctCount,
                eval.totalQuestions,
                prevMastery,
                eval.masteryResult.newMastery
            )
        }
    }

    fun sendTutorMessage(courseId: Long, query: String, skillId: Long? = null) {
        viewModelScope.launch {
            _isTutorThinking.value = true
            try {
                repository.askTutor(courseId, query, skillId)
            } finally {
                _isTutorThinking.value = false
            }
        }
    }

    fun togglePlanItemCompleted(itemId: Long, currentCompleted: Boolean) {
        viewModelScope.launch {
            repository.markPlanItemCompleted(itemId, !currentCompleted)
        }
    }
}
