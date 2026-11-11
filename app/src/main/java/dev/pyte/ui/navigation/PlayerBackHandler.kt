package dev.pyte.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/** Share NavHost's dispatcher so a committed gesture takes the same morph path as Close. */
@Composable
internal fun PlayerBackHandler(enabled: Boolean, onClose: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onClose,
    )
}
