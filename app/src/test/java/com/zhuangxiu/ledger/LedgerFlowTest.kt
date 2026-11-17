package com.zhuangxiu.ledger

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 启动真实 Activity/数据库，验证记账、导航和待付筛选，而非仅渲染静态组件。 */
@RunWith(RobolectricTestRunner::class)
@Config(application = LedgerApp::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LedgerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        val output = File("build/reports/ui/$name.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun recordUnpaidBillThenOpenPendingAndNavigateTabs() {
        awaitText("新家的第一笔花费，从这里开始")
        compose.onNodeWithText("记一笔", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("账单总金额").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("账单总金额").performTextInput("12800")
        compose.onNodeWithText("未付").performClick()
        capture("edit")
        compose.onNodeWithContentDescription("保存账单").performClick()
        awaitText("剩余待付")
        compose.onNodeWithText("剩余待付").performClick()
        awaitText("仅看待付")
        awaitText("共 1 笔，合计 ¥12,800.00")
        compose.onNodeWithText("仅看待付").assertIsSelected()
        capture("bills")
        compose.onNodeWithText("统计").performClick()
        awaitText("花费统计")
        capture("stats")
        compose.onNodeWithText("我的").performClick()
        awaitText("我的家装")
        capture("mine")
        compose.onNodeWithText("检查更新").performScrollTo().performClick()
        awaitText("当前版本 ${BuildConfig.VERSION_NAME}")
        compose.onNodeWithText("检查新版本").assertIsDisplayed()
        capture("update-idle")
        compose.onNodeWithText("设置 GitHub 访问").performScrollTo().performClick()
        awaitText("连接私有 GitHub 仓库")
        compose.runOnIdle { assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        compose.onNodeWithText("保存并检查").assertIsNotEnabled()
        compose.onNodeWithText("取消").performScrollTo().performClick()
        awaitText("检查新版本")
        compose.runOnIdle { assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0) }
    }
}
