package com.ethnym.ui

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ethnym.feature.settings.SettingsRoute
import com.ethnym.feature.wallets.ManageSheet
import com.ethnym.feature.wallets.WalletAction
import com.ethnym.feature.wallets.WalletActionScreen
import com.ethnym.ui.navigation.BottomSheetSceneStrategy
import com.ethnym.ui.navigation.CreateWallet
import com.ethnym.ui.navigation.DeleteWallet
import com.ethnym.ui.navigation.ExportWallet
import com.ethnym.ui.navigation.ImportWallet
import com.ethnym.ui.navigation.Main
import com.ethnym.ui.navigation.Manage
import com.ethnym.ui.navigation.Settings

@Composable
fun EthnymApp() {
    val backStack = rememberNavBackStack(Main)
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
    val back: () -> Unit = { backStack.removeLastOrNull() }
    // Leaves a wallet action, and the Manage sheet below it, for the tabs.
    val backToMain: () -> Unit = { backStack.retainAll { it == Main } }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // Scopes each ViewModel to its back stack entry, so it is cleared when the entry is popped.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        sceneStrategies = listOf(bottomSheetStrategy),
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
                    onManage = { backStack.add(Manage) },
                    onWalletAction = { action -> backStack.add(action.destination()) },
                    onOpenSettings = { backStack.add(Settings) },
                )
            }
            entry<Manage>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                ManageSheet(onChoose = { action -> backStack.add(action.destination()) }, onClose = back)
            }
            entry<CreateWallet> { WalletActionScreen(WalletAction.Create, onBack = back, onClose = backToMain) }
            entry<ImportWallet> { WalletActionScreen(WalletAction.Import, onBack = back, onClose = backToMain) }
            entry<ExportWallet> { WalletActionScreen(WalletAction.Export, onBack = back, onClose = backToMain) }
            entry<DeleteWallet> { WalletActionScreen(WalletAction.Delete, onBack = back, onClose = backToMain) }
            entry<Settings> {
                SettingsRoute(onBack = back)
            }
        },
    )
}

private fun WalletAction.destination(): NavKey = when (this) {
    WalletAction.Create -> CreateWallet
    WalletAction.Import -> ImportWallet
    WalletAction.Export -> ExportWallet
    WalletAction.Delete -> DeleteWallet
}
