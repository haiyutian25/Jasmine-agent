package com.lhzkml.jasmine.feature.main.impl.fonts

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.lhzkml.jasmine.core.data.repository.CustomFontRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presentation-side resolver from installed font IDs to Compose [FontFamily].
 *
 * File storage and download state live in core:data's [CustomFontRepository];
 * this cache only materializes [FontFamily] instances for the UI. Resolution
 * is main-thread safe: membership comes from the repository's in-memory
 * snapshot (no disk I/O) and families are cached per ID.
 */
@Singleton
class CustomFontFamilyCache @Inject constructor(
    private val customFontRepository: CustomFontRepository,
) {
    private val fontFamilyCache = ConcurrentHashMap<String, FontFamily>()

    /** Resolves an installed font ID to a cached [FontFamily], or null if missing. */
    fun fontFamilyFor(fontId: String): FontFamily? {
        val file = customFontRepository.installedFontFile(fontId) ?: return null
        return fontFamilyCache.getOrPut(fontId) { FontFamily(Font(file = file)) }
    }

    /** Evicts the cached family for [fontId] (call after the file is deleted). */
    fun evict(fontId: String) {
        fontFamilyCache.remove(fontId)
    }
}
