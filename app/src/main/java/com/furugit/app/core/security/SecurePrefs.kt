package com.furugit.app.core.security

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.furugit.app.App

/** SharedPreferences terenkripsi (Android Keystore) untuk data rahasia seperti token GitHub. */
object SecurePrefs {
    fun open(name: String): SharedPreferences {
        val ctx = App.instance
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx,
            name,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
