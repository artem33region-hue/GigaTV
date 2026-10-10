package com.artem.gigatv.ui.player

import androidx.media3.common.C
import java.util.Locale

private val languageCodes by lazy {
    Locale.getISOLanguages().associateBy { Locale(it).isO3Language }
}

internal fun readableTrackName(label: String?, language: String?, fallback: String): String {
    val name = label?.trim()?.takeIf { it.isNotEmpty() }
    val lang = language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
        ?.let { Locale(languageCodes[it] ?: it).getDisplayLanguage(Locale("ru")) }
        ?.replaceFirstChar { it.uppercase() }
    return when {
        name != null && lang != null && !name.equals(lang, ignoreCase = true) -> "$name · $lang"
        name != null && !name.equals(lang, ignoreCase = true) -> name
        lang != null -> "$lang · $fallback"
        name != null -> "$name · $fallback"
        else -> fallback
    }
}
