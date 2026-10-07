package com.ethnym.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ethnym.R
import com.ethnym.feature.activity.ActivityTab
import com.ethnym.feature.addressbook.AddressBookTab
import com.ethnym.feature.backup.BackupTab
import com.ethnym.feature.send.SendTab
import com.ethnym.feature.wallets.ManageWalletsTab
import com.ethnym.feature.wallets.WalletsTab
import com.ethnym.ui.theme.LocalDarkTheme

/** The web wallet's mobile tabs, in the same order. The bar fits five, so Settings opens from the top bar. */
enum class MainTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Wallets(R.string.tab_wallets, R.drawable.ic_account_balance_wallet),
    AddressBook(R.string.tab_address_book, R.drawable.ic_contacts),
    Send(R.string.tab_send, R.drawable.ic_arrow_outward),
    Activity(R.string.tab_activity, R.drawable.ic_receipt_long),
    Backup(R.string.tab_backup, R.drawable.ic_save),
}

/**
 * The tabs, under a top bar they all share: the logo on the left, Settings on the right, and nothing
 * else, so nothing in it is easy to hit by accident. Log out and the theme live in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onManageWallets: (ManageWalletsTab) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Wallets) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppLogo() },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        // Icon only; the tab name stays as the content description for screen readers.
                        icon = { Icon(painterResource(item.icon), contentDescription = stringResource(item.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            when (tab) {
                MainTab.Wallets -> WalletsTab(onManageWallets = onManageWallets)
                MainTab.AddressBook -> AddressBookTab()
                MainTab.Send -> SendTab()
                MainTab.Activity -> ActivityTab()
                MainTab.Backup -> BackupTab()
            }
        }
    }
}

/**
 * The ETHnym logo, drawn as is. The files swap on purpose: light mode shows the black tile and dark
 * mode the white one, so the tile stands out from the background instead of blending into it.
 */
@Composable
private fun AppLogo() {
    Image(
        painter = painterResource(if (LocalDarkTheme.current) R.drawable.ethnym_symbol_light else R.drawable.ethnym_symbol_dark),
        contentDescription = stringResource(R.string.app_name),
        modifier = Modifier.size(32.dp),
    )
}
