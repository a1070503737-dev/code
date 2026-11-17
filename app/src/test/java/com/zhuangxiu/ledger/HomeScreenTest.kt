package com.zhuangxiu.ledger

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zhuangxiu.ledger.domain.BudgetUsage
import com.zhuangxiu.ledger.domain.LedgerSummary
import com.zhuangxiu.ledger.ui.home.HomeContent
import com.zhuangxiu.ledger.ui.home.HomeUiState
import com.zhuangxiu.ledger.ui.navigation.LedgerBottomBar
import com.zhuangxiu.ledger.ui.navigation.Routes
import com.zhuangxiu.ledger.ui.theme.RenovationLedgerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(dark: Boolean = false, empty: Boolean = false, onPending: () -> Unit = {}, onAdd: () -> Unit = {}, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            RenovationLedgerTheme(darkTheme = dark) {
                HomeContent(
                    state = if (empty) HomeUiState(loading = false) else HomeUiState(
                        loading = false,
                        summary = LedgerSummary(8652000, 6842000, 1810000),
                        totalBudget = BudgetUsage(15000000, 8652000),
                        pendingCount = 3,
                        recent = listOf(
                            TestData.expense(1280000, paid = 800000, type = TestData.typeLabor),
                            TestData.expense(3680000, stage = TestData.stageWood),
                        ),
                    ),
                    bottomBar = { LedgerBottomBar(Routes.HOME, {}) },
                    onAdd = onAdd, onOpenExpense = {}, onOpenBills = {}, onOpenBudget = {}, onOpenPending = onPending,
                )
            }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val output = File("build/reports/ui/$name.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun pendingCardOpensUnpaidBills() {
        var opened = false
        render(onPending = { opened = true })
        compose.onNodeWithText("剩余待付").performClick()
        assertTrue(opened)
        capture("home-light")
    }

    @Test fun darkThemeKeepsTotalsReadable() {
        render(dark = true)
        compose.onNodeWithText("¥86,520.00").assertIsDisplayed()
        capture("home-dark")
    }

    @Test fun emptyLedgerCanStartFirstExpense() {
        var added = false
        render(empty = true, onAdd = { added = true })
        compose.onNodeWithText("记录第一笔").performScrollTo().performClick()
        assertTrue(added)
        capture("home-empty")
    }

    @Test @Config(qualifiers = "w320dp-h740dp-xhdpi")
    fun smallScreenWithLargeTextCanStillRecordExpense() {
        var added = false
        render(onAdd = { added = true }, fontScale = 1.3f)
        capture("home-large-text")
        compose.onNodeWithText("记一笔", useUnmergedTree = true).performClick()
        assertTrue(added)
    }
}
