package com.marcuspaulo.tarefas.sync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SessionStoreTest {
    private lateinit var context: Context
    private val cipher = object : SessionCipher {
        override fun encrypt(value: String) = value.reversed()
        override fun decrypt(value: String) = value.reversed()
    }
    private val a = Session("https://example.test", 1, "A", "a@example.test", "private-token-a")
    private val b = Session("https://example.test", 2, "B", "b@example.test", "private-token-b")

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("account_workspaces", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `primeira conta recebe banco local e segunda recebe banco separado`() {
        val store = SessionStore(context, cipher)
        assertEquals("tarefas.db", store.databaseName(null))
        store.connect(a)
        assertEquals("tarefas.db", store.databaseName(a))
        store.logout()
        assertNull(store.workspaceKey)
        assertNull(store.databaseName(null))
        store.connect(b)
        assertNotEquals(store.databaseName(a), store.databaseName(b))
        assertEquals(a.key, store.legacyOwner)
        store.logout()
        store.connect(a)
        assertEquals("tarefas.db", store.databaseName(a))
    }

    @Test fun `reinicio preserva identidade e token cifrado sem salvar senha`() {
        SessionStore(context, cipher).connect(a)
        val restored = SessionStore(context, cipher)
        assertEquals(a, restored.session.value)
        val stored = context.getSharedPreferences("session", Context.MODE_PRIVATE).all.toString()
        assertFalse(stored.contains(a.token))
        assertFalse(a.toString().contains(a.token))
        assertEquals(a.key, restored.legacyOwner)
    }

    @Test fun `resposta antiga de autenticacao nao expira outra sessao`() {
        val store = SessionStore(context, cipher)
        store.connect(a)
        store.connect(b)
        store.expire(a)
        assertFalse(store.session.value!!.expired)
        store.expire(b)
        assertTrue(store.session.value!!.expired)
        assertEquals("", store.session.value!!.token)
        assertNull(store.workspaceKey)
        assertNull(store.databaseName(store.session.value))
    }

    @Test fun `credencial ilegivel exige login sem liberar banco privado como local`() {
        SessionStore(context, cipher).connect(a)
        context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().putString("session", "invalid").commit()
        val restored = SessionStore(context, cipher)
        assertNull(restored.session.value)
        assertNull(restored.workspaceKey)
        assertEquals("tarefas.db", restored.databaseName(a))
    }

    @Test fun `sessao expirada de versao anterior bloqueia os dados e descarta o token ao reiniciar`() {
        SessionStore(context, cipher).connect(a)
        val auth = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        val old = JSONObject(cipher.decrypt(auth.getString("session", null)!!)).put("expired", true)
        auth.edit().putString("session", cipher.encrypt(old.toString())).commit()
        val restored = SessionStore(context, cipher)
        assertTrue(restored.session.value!!.expired)
        assertEquals("", restored.session.value!!.token)
        assertNull(restored.workspaceKey)
        assertNull(restored.databaseName(restored.session.value))
        assertNull(restored.databaseName(null))
        assertFalse(cipher.decrypt(auth.getString("session", null)!!).contains(a.token))
        restored.connect(a.copy(token = "renewed-token"))
        assertEquals(a.key, restored.workspaceKey)
        assertEquals("tarefas.db", restored.databaseName(restored.session.value))
    }
}
