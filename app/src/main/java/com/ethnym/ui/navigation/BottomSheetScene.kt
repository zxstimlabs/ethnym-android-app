package com.ethnym.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.coroutines.launch

/**
 * Slides away the sheet the current entry is shown in, then runs the given action, such as
 * navigating. Outside a sheet it runs the action straight away.
 */
val LocalDismissSheet = staticCompositionLocalOf<(then: () -> Unit) -> Unit> { { then -> then() } }

/** An [OverlayScene] that shows [entry] in a [ModalBottomSheet] over the entries below it. */
@OptIn(ExperimentalMaterial3Api::class)
private data class BottomSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = {
        val lifecycleOwner = rememberLifecycleOwner()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        // A second tap while the sheet slides away would otherwise run a second action.
        var dismissing by remember { mutableStateOf(false) }
        val dismiss: (() -> Unit) -> Unit = { then ->
            if (!dismissing) {
                dismissing = true
                scope.launch {
                    sheetState.hide()
                    then()
                }
            }
        }
        ModalBottomSheet(
            onDismissRequest = onBack,
            sheetState = sheetState,
            // White on light and a raised grey on dark, as iOS draws sheets. The default is a
            // near-black that barely shows against the dark background.
            containerColor = MaterialTheme.colorScheme.surfaceBright,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner, LocalDismissSheet provides dismiss) {
                entry.Content()
            }
        }
    }
}

/**
 * Shows entries marked with [bottomSheet] in a sheet over the entries below them, so a back step
 * from the screen above the sheet returns to the sheet. Must come before strategies that would
 * claim those entries themselves.
 */
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        if (!lastEntry.metadata.contains(BottomSheetKey)) return null
        @Suppress("UNCHECKED_CAST")
        return BottomSheetScene(
            key = lastEntry.contentKey as T,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = lastEntry,
            onBack = onBack,
        )
    }

    companion object {
        /** Entry metadata that shows the entry in a sheet. */
        fun bottomSheet(): Map<String, Any> = metadata { put(BottomSheetKey, Unit) }

        private object BottomSheetKey : NavMetadataKey<Unit>
    }
}
