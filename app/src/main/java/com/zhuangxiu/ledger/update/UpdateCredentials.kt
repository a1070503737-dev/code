package com.zhuangxiu.ledger.update

import com.zhuangxiu.ledger.BuildConfig

/** Shared read-only access is explicitly supplied by the owner at release build time. */
internal object UpdateCredentials {
    val bundled: Boolean get() = BuildConfig.BUNDLED_GITHUB_TOKEN.isNotBlank()

    fun resolve(storedToken: () -> String, bundledToken: String = BuildConfig.BUNDLED_GITHUB_TOKEN): String =
        bundledToken.takeIf { it.isNotBlank() } ?: storedToken()
}
