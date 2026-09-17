package com.networktoolbox.core.designsystem

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Presentation-only resource reference. Never persisted in a domain or history model. */
data class UiText(@StringRes val resource: Int, val arguments: List<Any> = emptyList(), val raw: String? = null) {
    constructor(@StringRes resource: Int, vararg arguments: Any) : this(resource, arguments.toList())
    constructor(raw: String) : this(0, raw = raw)

    @Composable
    fun resolve(): String {
        raw?.let { return it }
        val resolved = arguments.map { if (it is UiText) it.resolve() else it }
        return stringResource(resource, *resolved.toTypedArray())
    }

    fun resolve(context: Context): String = raw ?: context.getString(
        resource, *arguments.map { if (it is UiText) it.resolve(context) else it }.toTypedArray(),
    )
}
