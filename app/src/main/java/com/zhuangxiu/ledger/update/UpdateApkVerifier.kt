package com.zhuangxiu.ledger.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal data class ApkIdentity(val packageName: String, val versionCode: Long, val versionName: String, val signers: Set<String>)

internal object UpdateApkVerifier {
    @Suppress("DEPRECATION")
    fun verify(context: Context, file: File, release: AppRelease) {
        if (!file.isFile || file.length() != release.size || VerifiedDownload.sha256(file) != release.sha256) {
            throw IOException("安装包已失效，请重新下载")
        }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        val archive = context.packageManager.getPackageArchiveInfo(file.path, flags)
            ?: throw IOException("无法读取安装包，请重新下载")
        validateIdentity(identity(current), identity(archive), release.version.toString(), release.versionCode)
        if ((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT) {
            throw IOException("此版本需要更高版本的 Android，当前手机无法安装")
        }
    }

    fun validateIdentity(current: ApkIdentity, update: ApkIdentity, expectedVersion: String, expectedCode: Long? = null) {
        if (update.packageName != current.packageName) throw IOException("安装包不属于装修记账，已停止安装")
        if (update.versionCode <= current.versionCode || update.versionName != expectedVersion ||
            (expectedCode != null && update.versionCode != expectedCode)) {
            throw IOException("安装包版本不符合更新要求，请重新检查更新")
        }
        if (current.signers.isEmpty() || update.signers != current.signers) {
            throw IOException("安装包签名与当前应用不同，无法覆盖更新。请联系维护者")
        }
    }

    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): ApkIdentity {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return ApkIdentity(info.packageName, PackageInfoCompat.getLongVersionCode(info), info.versionName.orEmpty(),
            signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet())
    }
}
