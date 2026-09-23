package com.lhzkml.jasmine.core.data.model

/**
 * Who authored a chat message.
 *
 * Lives in the data layer because it is part of the persisted transcript (and of
 * the history replayed into a model session), not a UI-only concept.
 */
enum class ChatRole(val id: String) {
    USER("user"),
    ASSISTANT("assistant"),
    ;

    companion object {
        /**
         * Unknown values fall back to [ASSISTANT]: an unrecognised row is
         * rendered as model output rather than being attributed to the user.
         */
        fun fromId(id: String): ChatRole = entries.firstOrNull { it.id == id } ?: ASSISTANT
    }
}
