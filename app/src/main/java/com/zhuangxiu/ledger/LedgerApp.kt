package com.zhuangxiu.ledger

import android.app.Application
import androidx.work.Configuration
import com.zhuangxiu.ledger.notification.Notifier
import com.zhuangxiu.ledger.notification.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** 应用入口：初始化 Hilt、通知渠道和每日提醒任务 */
@HiltAndroidApp
class LedgerApp : Application(), Configuration.Provider {

    // 支持未运行自动初始化 Provider 的宿主按需取得默认 WorkManager。
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    @Inject
    lateinit var notifier: Notifier

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        ReminderWorker.schedule(this)
    }
}
