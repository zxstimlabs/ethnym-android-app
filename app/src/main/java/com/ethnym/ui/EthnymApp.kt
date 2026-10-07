package com.ethnym.ui

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ethnym.feature.settings.SettingsRoute
import com.ethnym.feature.wallets.ManageWalletsRoute
import com.ethnym.ui.navigation.Main
import com.ethnym.ui.navigation.ManageWallets
import com.ethnym.ui.navigation.Settings

@Composable
fun EthnymApp() {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // Scopes each ViewModel to its back stack entry, so it is cleared when the entry is popped.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // Pushed screens slide in from the right; back (including predictive back) reverses it.
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    onManageWallets = { tab -> backStack.add(ManageWallets(tab)) },
                    onOpenSettings = { backStack.add(Settings) },
                )
            }
            entry<ManageWallets> { key ->
                ManageWalletsRoute(initialTab = key.tab, onBack = { backStack.removeLastOrNull() })
            }
            entry<Settings> {
                SettingsRoute(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
