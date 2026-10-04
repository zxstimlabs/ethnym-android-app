package com.ethnym.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Navigation 3 destinations. Keys are @Serializable so the back stack survives process death.

/** The tabbed home: Wallets, Address Book, Send, Activity, Backup, Settings. */
@Serializable
data object Main : NavKey

/** Create, export, import and delete wallets. */
@Serializable
data object ManageWallets : NavKey
