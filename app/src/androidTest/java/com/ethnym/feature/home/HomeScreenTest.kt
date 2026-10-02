package com.ethnym.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ethnym.ui.theme.EthnymTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hiddenBalances_areMasked() {
        composeTestRule.setContent {
            EthnymTheme {
                HomeScreen(uiState = HomeUiState.Ready(hideBalances = true), onOpenSettings = {})
            }
        }

        composeTestRule.onNodeWithText("••••••").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 ETH").assertDoesNotExist()
    }
}
