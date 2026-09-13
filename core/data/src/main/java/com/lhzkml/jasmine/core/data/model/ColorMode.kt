package com.lhzkml.jasmine.core.data.model

/**
 * How the app chooses between the light and dark variant of the selected
 * palette family. Persisted as [id].
 *
 * Mirrors the platform "follow system" dark-theme behavior: [SYSTEM] resolves
 * against the OS dark-mode setting at runtime, [LIGHT]/[DARK] pin a variant.
 */
enum class ColorMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String): ColorMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}
