package com.lhzkml.jasmine.core.agent.tools

import com.google.adk.kt.annotations.Tool
import com.lhzkml.jasmine.core.agent.ConversationStore
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Most recent conversations worth listing before the answer stops being useful. */
private const val MaxConversations = 20

private val TimestampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * The app's own tools, declared with ADK's [Tool] annotation.
 *
 * ADK's KSP processor turns each annotated function into a `FunctionTool` — schema,
 * argument extraction and result wrapping included — so nothing here describes a
 * declaration by hand. The processor emits a `generatedTools()` accessor for this
 * class (a class rather than top-level functions so a tool can take injected
 * collaborators); the composition root calls it.
 */
class JasmineTools(
    private val conversationStore: ConversationStore,
) {

    /**
     * Reports the device's current date and time.
     *
     * A model has no clock: without this it answers date questions from its training
     * cut-off and gets them wrong. The zone is part of the answer because "today" is
     * zone-dependent.
     */
    @Tool(
        name = "current_time",
        description =
            "Get the current date, time and time zone on the user's device. " +
                "Use this whenever the answer depends on what \"now\" is.",
    )
    fun currentTime(): String =
        ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzz"))

    /**
     * Lists the user's earlier conversations in this app, newest first.
     *
     * Titles only — never transcripts: the model needs to know *that* something was
     * discussed and when, and dumping old messages would blow up the context for no
     * gain. Reads the repository's snapshot rather than collecting, because a tool
     * answer is a point-in-time fact.
     */
    @Tool(
        name = "list_past_conversations",
        description =
            "List the user's earlier conversations in this app, newest first, with their " +
                "titles and last-updated times. Use it when the user refers to something " +
                "discussed before, or asks what they have talked about.",
    )
    fun listPastConversations(): String {
        val conversations = conversationStore.conversationsStateFlow.value
        if (conversations.isEmpty()) return "The user has no earlier conversations."

        val zone = ZoneId.systemDefault()
        return conversations.take(MaxConversations).joinToString(separator = "\n") { conversation ->
            val updated = Instant.ofEpochMilli(conversation.updatedAt)
                .atZone(zone)
                .format(TimestampFormat)
            "- $updated — ${conversation.title}"
        }
    }
}
