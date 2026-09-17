package com.networktoolbox

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/** Small identity-keyed offsets only; never stores a report or a device. */
internal class RouteScrollStates {
    private val states = linkedMapOf<String, ScrollState>()

    fun forKey(key: String): ScrollState {
        val existing = states.remove(key)
        val state = existing ?: ScrollState(0)
        states[key] = state
        while (states.size > MAX_ENTRIES) states.remove(states.keys.first())
        return state
    }

    companion object {
        private const val MAX_ENTRIES = 32
        val Saver: Saver<RouteScrollStates, Any> = listSaver(
            save = { owner -> owner.states.flatMap { (key, state) -> listOf(key, state.value) } },
            restore = { values ->
                RouteScrollStates().apply {
                    values.chunked(2).takeLast(MAX_ENTRIES).forEach { pair ->
                        val key = pair.getOrNull(0) as? String
                        val offset = pair.getOrNull(1) as? Int
                        if (key != null && offset != null) states[key] = ScrollState(offset.coerceAtLeast(0))
                    }
                }
            },
        )
    }
}
