package com.zhuangxiu.ledger

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zhuangxiu.ledger.ui.mine.UpdateContent
import com.zhuangxiu.ledger.ui.mine.UpdateStage
import com.zhuangxiu.ledger.ui.mine.UpdateUiState
import com.zhuangxiu.ledger.ui.theme.RenovationLedgerTheme
import com.zhuangxiu.ledger.update.AppRelease
import com.zhuangxiu.ledger.update.AppVersion
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
class UpdateScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val release = AppRelease(AppVersion(1, 0, 7), "改善使用体验\n修复已知问题", 123, 3_000_000, "a".repeat(64))
    private fun render(state: UpdateUiState, download: () -> Unit = {}, cancel: () -> Unit = {}, install: () -> Unit = {}) {
        compose.setContent {
            RenovationLedgerTheme {
                UpdateContent(state.copy(accessConfigured = true), {}, {}, download, cancel, install, {})
            }
        }
    }

    @Test fun newerReleaseCanBeDownloadedInApp() {
        var clicked = false
        render(UpdateUiState(UpdateStage.AVAILABLE, release), download = { clicked = true })
        compose.onNodeWithText("下载更新").performClick()
        assertTrue(clicked)
        val file = File("build/reports/ui/update-available.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun downloadingCanBeCancelledButCannotBeInstalled() {
        var cancelled = false
        render(UpdateUiState(UpdateStage.DOWNLOADING, release, downloaded = 1_500_000), cancel = { cancelled = true })
        compose.onNodeWithText("50% · 1.4 / 2.9 MB").assertIsDisplayed()
        compose.onNodeWithText("安装更新").assertDoesNotExist()
        compose.onNodeWithText("取消下载").performClick()
        assertTrue(cancelled)
    }

    @Test fun verifiedPackageOffersInstallation() {
        var installed = false
        render(UpdateUiState(UpdateStage.READY, release), install = { installed = true })
        compose.onNodeWithText("安装更新").performClick()
        assertTrue(installed)
    }

    @Test fun sharedAccessDoesNotAskEachUserForAToken() {
        render(UpdateUiState(bundledAccess = true, accessConfigured = true))
        compose.onNodeWithText("优先访问公开发行版，无需填写令牌。私有访问使用内置授权。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("设置 GitHub 访问").assertDoesNotExist()
        compose.onNodeWithText("管理访问设置").assertDoesNotExist()
        val file = File("build/reports/ui/update-shared-access.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
