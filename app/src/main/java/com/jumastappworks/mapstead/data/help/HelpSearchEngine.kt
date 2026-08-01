package com.jumastappworks.mapstead.data.help

object HelpSearchEngine {
    fun searchHelpTopics(
        topics: List<HelpTopic>,
        resolvedTopics: Map<HelpTopicId, ResolvedHelpTopic>,
        query: String
    ): List<HelpTopic> {
        if (query.isBlank()) return topics

        val normalizedQuery = query.trim().lowercase().replace(Regex("\\s+"), " ")
        
        return topics.mapNotNull { topic ->
            val resolved = resolvedTopics[topic.id] ?: return@mapNotNull null
            val score = calculateScore(resolved, normalizedQuery)
            if (score > 0) topic to score else null
        }
        .sortedWith(compareByDescending<Pair<HelpTopic, Int>> { it.second }.thenBy { topics.indexOf(it.first) })
        .map { it.first }
    }

    private fun calculateScore(topic: ResolvedHelpTopic, query: String): Int {
        var score = 0
        val title = topic.title.lowercase()
        
        // Exact title match
        if (title == query) score += 1000
        // Title starts with
        else if (title.startsWith(query)) score += 500
        // Title contains
        else if (title.contains(query)) score += 200
        
        // Keyword match
        if (topic.keywords.any { it.lowercase().contains(query) }) score += 300
        
        // Summary match
        if (topic.summary.lowercase().contains(query)) score += 100
        
        // Heading match
        if (topic.sections.any { it.heading.lowercase().contains(query) }) score += 50
        
        // Body match
        if (topic.sections.any { it.body.lowercase().contains(query) }) score += 10
        
        return score
    }
}
