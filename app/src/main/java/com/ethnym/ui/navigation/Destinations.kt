package com.ethnym.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Navigation 3 destinations. Keys are @Serializable so the back stack survives process death.

/** The tabbed home: Wallets, Address Book, Send, Activity, Backup. */
@Serializable
data object Main : NavKey

/** Create, import, export and delete, in a sheet over the Wallets tab. */
@Serializable
data object Manage : NavKey

// Each wallet action has its own screen, opened from the Manage sheet or, before the first wallet
// exists, straight from the Wallets tab.

@Serializable
data object CreateWallet : NavKey

@Serializable
data object ImportWallet : NavKey

@Serializable
data object ExportWallet : NavKey

@Serializable
data object DeleteWallet : NavKey

/** The Add Contact form, in a sheet over the Address Book tab. */
@Serializable
data object AddContact : NavKey

// Each backup tool has its own screen, opened from the Backup tab.

@Serializable
data object KeystoreTool : NavKey

@Serializable
data object LocalBackup : NavKey

@Serializable
data object CloudSync : NavKey

/** RPC endpoint, offline mode, appearance and log out. Opens from the top bar. */
@Serializable
data object Settings : NavKey
