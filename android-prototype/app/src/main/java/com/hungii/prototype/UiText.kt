package com.hungii.prototype

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/** User-visible text from state holders: app strings are resources; backend messages arrive translated. */
sealed interface UiText {
    data class Raw(
        val value: String,
    ) : UiText

    data class Res(
        @param:StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Plural(
        @param:PluralsRes val id: Int,
        val count: Int,
        val args: List<Any> = listOf(count),
    ) : UiText

    data class Joined(
        val parts: List<UiText>,
    ) : UiText

    companion object {
        fun of(
            @StringRes id: Int,
            vararg args: Any,
        ): UiText = Res(id, args.toList())
    }
}

@Composable
fun UiText.asString(): String =
    when (this) {
        is UiText.Raw -> value
        is UiText.Res -> stringResource(id, *args.toTypedArray())
        is UiText.Plural -> pluralStringResource(id, count, *args.toTypedArray())
        is UiText.Joined -> parts.map { it.asString() }.joinToString(" ")
    }
