package com.marcuspaulo.tarefas.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.marcuspaulo.tarefas.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Session(
    val server: String, val userId: Long, val name: String, val email: String,
    val token: String, val expired: Boolean = false, val emailVerified: Boolean = false
) {
    val key: String get() = MessageDigest.getInstance("SHA-256")
        .digest("$server/users/$userId".toByteArray()).joinToString("") { "%02x".format(it) }
    override fun toString(): String = "Session(account=$key, expired=$expired)"
}

internal interface SessionCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
}

private class KeystoreSessionCipher : SessionCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("tarefas_session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("tarefas_session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    override fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}

/** O vínculo do banco antigo é durável e separado da credencial descartável. */
class SessionStore internal constructor(context: Context, private val cipher: SessionCipher = KeystoreSessionCipher()) {
    private val auth = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val workspaces = context.getSharedPreferences("account_workspaces", Context.MODE_PRIVATE)
    private val mutableSession = MutableStateFlow(load())
    val session = mutableSession.asStateFlow()
    val legacyOwner: String? get() = workspaces.getString("legacy_owner", null)
    val workspaceKey: String? get() = when (val current = session.value) {
        null -> if (legacyOwner == null) "local" else null
        else -> if (current.expired) null else current.key
    }
    val lastServer: String get() = workspaces.getString("server", null)
        ?: if (BuildConfig.DEBUG) "http://10.0.2.2:8000" else ""

    @Synchronized
    fun connect(value: Session) {
        val encoded = encode(value)
        check(workspaces.edit().putString("legacy_owner", legacyOwner ?: value.key)
            .putString("server", value.server).commit()) { "Não foi possível salvar a conta no aparelho." }
        check(auth.edit().putString("session", encoded).commit()) { "Não foi possível salvar a sessão." }
        mutableSession.value = value
    }

    @Synchronized
    fun expire(expected: Session) {
        if (!isCurrent(expected)) return
        val expired = session.value!!.copy(token = "", expired = true)
        check(auth.edit().putString("session", encode(expired)).commit())
        mutableSession.value = expired
    }

    /** Mudanças no status do e-mail não invalidam uma sincronização em andamento. */
    fun isCurrent(expected: Session): Boolean = session.value?.let {
        it.key == expected.key && it.token == expected.token && it.expired == expected.expired
    } == true

    @Synchronized
    fun updateVerification(expected: Session, verified: Boolean) {
        if (session.value != expected || expected.expired) return
        val current = session.value!!
        if (current.emailVerified == verified) return
        val updated = current.copy(emailVerified = verified)
        check(auth.edit().putString("session", encode(updated)).commit())
        mutableSession.value = updated
    }

    @Synchronized
    fun logout() {
        check(auth.edit().clear().commit()) { "Não foi possível encerrar a sessão local." }
        mutableSession.value = null
    }

    fun databaseName(value: Session?): String? = when {
        value?.expired == true -> null
        value != null && value.key == legacyOwner -> "tarefas.db"
        value != null -> "account_${value.key}.db"
        legacyOwner == null -> "tarefas.db"
        else -> null
    }

    private fun encode(value: Session): String = cipher.encrypt(JSONObject()
        .put("server", value.server).put("id", value.userId).put("name", value.name)
        .put("email", value.email).put("token", value.token).put("expired", value.expired)
        .put("email_verified", value.emailVerified).toString())

    private fun load(): Session? = runCatching {
        val stored = auth.getString("session", null) ?: return null
        val json = JSONObject(cipher.decrypt(stored))
        val loaded = Session(json.getString("server"), json.getLong("id"), json.getString("name"),
            json.getString("email"), json.getString("token"), json.optBoolean("expired"), json.optBoolean("email_verified"))
        if (loaded.expired && loaded.token.isNotEmpty()) {
            loaded.copy(token = "").also {
                check(auth.edit().putString("session", encode(it)).commit())
            }
        } else loaded
    }.getOrNull()

    companion object {
        @Volatile private var instance: SessionStore? = null
        fun get(context: Context): SessionStore = instance ?: synchronized(this) {
            instance ?: SessionStore(context.applicationContext).also { instance = it }
        }
    }
}
