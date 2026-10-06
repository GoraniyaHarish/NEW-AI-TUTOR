package com.example.ai.nlp

enum class TutorIntent {
    EXPLAIN,
    SIMPLIFY,
    EXAMPLE,
    HINT,
    QUIZ,
    SUMMARY,
    COMPARE,
    REVISE,
    ANSWER_CHECK,
    FOLLOW_UP
}

data class AnalyzedQuery(
    val originalQuery: String,
    val intent: TutorIntent,
    val focusTerms: List<String>,
    val isInformal: Boolean,
    val isFollowUp: Boolean
)

object QueryUnderstanding {

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "in", "on", "at", "to", "for", "with", "of", "by",
        "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does", "did",
        "can", "could", "will", "would", "should", "me", "my", "you", "your", "it", "this",
        "that", "these", "those", "please", "bhai", "sir", "kya", "hai", "ko", "se", "ka",
        "ki", "ke", "par", "mein", "aur", "bhi"
    )

    fun analyze(
        query: String,
        currentSkillName: String? = null,
        lastAssistantMessage: String? = null
    ): AnalyzedQuery {
        val qLower = query.lowercase().trim()

        val isInformal = qLower.contains("bhai") ||
                qLower.contains("samjha") ||
                qLower.contains("yaar") ||
                qLower.contains("bro") ||
                qLower.contains("pls") ||
                qLower.contains("eli5") ||
                qLower.contains("kuch") ||
                qLower.contains("batana")

        val isFollowUp = qLower.contains("second part") ||
                qLower.contains("first part") ||
                qLower.contains("last part") ||
                qLower.contains("above") ||
                qLower.contains("previous") ||
                qLower.contains("what about that") ||
                qLower.contains("why is that") ||
                qLower.contains("what does that mean") ||
                qLower.contains("uske baad") ||
                qLower.startsWith("and ") ||
                qLower.startsWith("but ")

        val intent = when {
            // Hint / Stuck
            qLower.contains("hint") ||
                    qLower.contains("clue") ||
                    qLower.contains("stuck") ||
                    qLower.contains("how do i start") ||
                    qLower.contains("where to begin") ||
                    qLower.contains("give me a push") ||
                    qLower.contains("kahan se shuru") -> TutorIntent.HINT

            // Simplify / Confused
            qLower.contains("don't understand") ||
                    qLower.contains("dont understand") ||
                    qLower.contains("don't get") ||
                    qLower.contains("dont get") ||
                    qLower.contains("confused") ||
                    qLower.contains("too hard") ||
                    qLower.contains("simple words") ||
                    qLower.contains("simpler") ||
                    qLower.contains("simplify") ||
                    qLower.contains("like i am 5") ||
                    qLower.contains("eli5") ||
                    qLower.contains("easy terms") ||
                    qLower.contains("asan bhasha") ||
                    qLower.contains("aasan") ||
                    qLower.contains("samajh nahi") ||
                    qLower.contains("samajh ni") -> TutorIntent.SIMPLIFY

            // Example
            qLower.contains("example") ||
                    qLower.contains("for instance") ||
                    qLower.contains("real world") ||
                    qLower.contains("practical") ||
                    qLower.contains("worked problem") ||
                    qLower.contains("sample") ||
                    qLower.contains("udaharun") ||
                    qLower.contains("koi example") -> TutorIntent.EXAMPLE

            // Quiz / Test
            qLower.contains("quiz me") ||
                    qLower.contains("test me") ||
                    qLower.contains("ask me a question") ||
                    qLower.contains("practice question") ||
                    qLower.contains("test my knowledge") ||
                    qLower.contains("swal puchho") ||
                    qLower.contains("sawal") -> TutorIntent.QUIZ

            // Compare
            qLower.contains("difference between") ||
                    qLower.contains("diff between") ||
                    qLower.contains("vs") ||
                    qLower.contains("versus") ||
                    qLower.contains("compare") ||
                    qLower.contains("distinguish") -> TutorIntent.COMPARE

            // Summary / Quick recap
            qLower.contains("summary") ||
                    qLower.contains("summarize") ||
                    qLower.contains("recap") ||
                    qLower.contains("short notes") ||
                    qLower.contains("cheat sheet") ||
                    qLower.contains("overview") ||
                    qLower.contains("in brief") -> TutorIntent.SUMMARY

            // Revision
            qLower.contains("revise") ||
                    qLower.contains("revision") ||
                    qLower.contains("quick review") -> TutorIntent.REVISE

            // Answer check
            qLower.contains("is this correct") ||
                    qLower.contains("did i get this right") ||
                    qLower.contains("check my answer") ||
                    qLower.contains("am i right") -> TutorIntent.ANSWER_CHECK

            // Follow-up
            isFollowUp -> TutorIntent.FOLLOW_UP

            // Default
            else -> TutorIntent.EXPLAIN
        }

        val focusTerms = extractFocusTerms(qLower, currentSkillName)

        return AnalyzedQuery(
            originalQuery = query,
            intent = intent,
            focusTerms = focusTerms,
            isInformal = isInformal,
            isFollowUp = isFollowUp
        )
    }

    private fun extractFocusTerms(query: String, currentSkillName: String?): List<String> {
        val clean = query.replace(Regex("[^a-zA-Z0-9\\s]"), " ")
        val tokens = clean.split(Regex("\\s+"))
            .filter { it.length > 2 && !STOP_WORDS.contains(it) }

        val terms = mutableListOf<String>()
        if (!currentSkillName.isNullOrBlank()) {
            terms.add(currentSkillName.trim())
        }
        terms.addAll(tokens)
        return terms.distinct()
    }
}
