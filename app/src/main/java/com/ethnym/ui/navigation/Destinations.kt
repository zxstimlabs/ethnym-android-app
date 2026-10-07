package com.ethnym.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.ethnym.feature.wallets.ManageWalletsTab
import kotlinx.serialization.Serializable

// Navigation 3 destinations. Keys are @Serializable so the back stack survives process death.

/** The tabbed home: Wallets, Address Book, Send, Activity, Backup. */
@Serializable
data object Main : NavKey

/** Create, export, import and delete wallets, opened on [tab]. */
@Serializable
data class ManageWallets(val tab: ManageWalletsTab = ManageWalletsTab.Create) : NavKey

/** RPC endpoint, offline mode, appearance and log out. Opens from the top bar. */
@Serializable
data object Settings : NavKey
