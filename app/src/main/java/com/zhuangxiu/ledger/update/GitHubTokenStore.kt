package com.zhuangxiu.ledger.update

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** The user's token stays on this device, encrypted with a non-exportable Android Keystore key. */
@Singleton
class GitHubTokenStore @Inject constructor(@param:ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("github_update_access", Context.MODE_PRIVATE)

    @Synchronized fun read(): String {
        val ciphertext = preferences.getString("encrypted_token", null) ?: return ""
        return try { TokenCipher.decrypt(ciphertext, key()) }
        catch (_: Exception) { throw IOException("GitHub 访问设置已失效，请重新设置只读令牌") }
    }

    @Synchronized fun save(token: String) {
        val ciphertext = TokenCipher.encrypt(token, key())
        if (!preferences.edit().putString("encrypted_token", ciphertext).commit()) {
            throw IOException("无法保存访问设置，请检查手机存储空间")
        }
    }

    @Synchronized fun clear() {
        if (!preferences.edit().remove("encrypted_token").commit()) throw IOException("无法移除访问设置，请重试")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    private companion object { const val KEY_ALIAS = "renovation-ledger-github-update-access" }
}

internal object TokenCipher {
    fun encrypt(token: String, key: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encode = Base64.getEncoder()
        return encode.encodeToString(cipher.iv) + ":" + encode.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(payload: String, key: SecretKey): String {
        val parts = payload.split(':')
        require(parts.size == 2)
        val decode = Base64.getDecoder()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, decode.decode(parts[0])))
        return cipher.doFinal(decode.decode(parts[1])).toString(Charsets.UTF_8)
    }
}
