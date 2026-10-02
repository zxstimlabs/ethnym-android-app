package com.ethnym.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ethnym.feature.home.HomeRoute
import com.ethnym.feature.settings.SettingsRoute
import com.ethnym.ui.navigation.Home
import com.ethnym.ui.navigation.Settings

@Composable
fun EthnymApp() {
    val backStack = rememberNavBackStack(Home)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // Scopes each ViewModel to its back stack entry, so it is cleared when the entry is popped.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Home> {
                HomeRoute(onOpenSettings = { backStack.add(Settings) })
            }
            entry<Settings> {
                SettingsRoute(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
