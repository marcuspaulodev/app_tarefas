package com.marcuspaulo.tarefas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcuspaulo.tarefas.sync.HttpSyncApi
import com.marcuspaulo.tarefas.sync.ApiException
import com.marcuspaulo.tarefas.sync.EmailVerificationResponse
import com.marcuspaulo.tarefas.sync.SessionStore
import com.marcuspaulo.tarefas.sync.SyncScheduler
import com.marcuspaulo.tarefas.sync.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountScreen(store: SessionStore, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val session by store.session.collectAsStateWithLifecycle()
    val progress by SyncStatus.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(session?.server ?: store.lastServer) }
    var email by remember { mutableStateOf(session?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    var forgotPassword by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun verifyEmail(resend: Boolean) {
        val current = session?.takeUnless { it.expired } ?: return
        scope.launch {
            busy = true
            error = null
            notice = null
            try {
                val api = HttpSyncApi()
                val result = if (resend) api.requestEmailVerification(current) else {
                    val verified = api.validateSession(current)
                    EmailVerificationResponse(verified,
                        if (verified) "E-mail confirmado." else "A confirmação ainda está pendente. Abra o link enviado para seu e-mail.")
                }
                withContext(Dispatchers.IO) { store.updateVerification(current, result.verified) }
                if (store.isCurrent(current)) notice = result.message
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (failure is ApiException && failure.status == 401) {
                    withContext(Dispatchers.IO) { store.expire(current) }
                }
                if (store.isCurrent(current)) error = failure.message ?: "Não foi possível verificar o e-mail. Tente novamente."
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (onBack != null) TextButton(onClick = onBack, enabled = !busy) { Text("Voltar às tarefas") }
        Text(if (forgotPassword) "Recuperar senha" else "Conta e sincronização", style = MaterialTheme.typography.headlineMedium)
        if (session != null && !session!!.expired) {
            Text(session!!.name, style = MaterialTheme.typography.titleLarge)
            Text(session!!.email)
            Text(if (session!!.emailVerified) "E-mail confirmado" else "E-mail pendente de confirmação",
                style = MaterialTheme.typography.titleMedium)
            if (!session!!.emailVerified) {
                Text("Abra o link de confirmação no navegador. Você pode continuar usando suas tarefas enquanto isso.")
                TextButton(onClick = { verifyEmail(true) }, enabled = !busy) { Text("Enviar link de confirmação") }
                TextButton(onClick = { verifyEmail(false) }, enabled = !busy) { Text("Já confirmei") }
            }
            Text("Suas tarefas continuam disponíveis sem internet. As alterações são enviadas quando houver conexão.")
            Text(session!!.server, style = MaterialTheme.typography.bodySmall)
            if (progress.account == session!!.key) {
                Text(if (progress.running) "Sincronizando…" else progress.message ?: "Sincronização automática ativada.")
            }
            Button(onClick = { SyncScheduler.request(context) }, enabled = !progress.running) { Text("Sincronizar agora") }
            TextButton(onClick = { confirmLogout = true }, enabled = !busy) { Text("Sair da conta") }
        } else {
            Text(if (forgotPassword) "Informe o e-mail da sua conta. Você receberá um link para criar uma nova senha no navegador e depois poderá entrar aqui."
                else if (session?.expired == true) "Sua sessão foi encerrada. Entre novamente para acessar suas tarefas. As alterações salvas neste aparelho foram preservadas."
                else "Entre com a mesma conta usada no site.")
            if (!forgotPassword && store.legacyOwner == null) Text("As tarefas e tags deste aparelho serão vinculadas a esta conta no primeiro acesso.")
            OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("Endereço do servidor") },
                placeholder = { Text("https://tarefas.exemplo.com") }, singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail") }, singleLine = true,
                enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            if (!forgotPassword) OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Senha") }, singleLine = true,
                enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && email.isNotBlank() && (forgotPassword || password.isNotEmpty()) && server.isNotBlank(), onClick = {
                scope.launch {
                    busy = true
                    error = null
                    notice = null
                    try {
                        if (forgotPassword) {
                            notice = HttpSyncApi().requestPasswordReset(server, email)
                        } else {
                            val connected = HttpSyncApi().login(server, email, password)
                            withContext(Dispatchers.IO) { store.connect(connected) }
                            password = ""
                            onBack?.invoke()
                        }
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (failure: Exception) {
                        error = if (failure is java.net.ConnectException || failure is java.net.SocketTimeoutException)
                            "Não foi possível conectar. Confira o endereço do servidor e sua conexão."
                        else failure.message ?: if (forgotPassword) "Não foi possível solicitar o link. Tente novamente."
                            else "Não foi possível entrar. Tente novamente."
                    } finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (forgotPassword) "Enviar link" else "Entrar") }
            TextButton(enabled = !busy, onClick = {
                forgotPassword = !forgotPassword
                password = ""
                error = null
                notice = null
            }) { Text(if (forgotPassword) "Voltar para entrar" else "Esqueci minha senha") }
            if (session != null) TextButton(onClick = { confirmLogout = true }, enabled = !busy) { Text("Sair desta conta") }
        }
        if (busy) CircularProgressIndicator()
        notice?.let { Text(it) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false }, title = { Text("Sair da conta?") },
        text = { Text("Alterações ainda não enviadas ficam neste aparelho e serão sincronizadas quando você entrar novamente nesta conta.") },
        confirmButton = { TextButton(onClick = {
            confirmLogout = false
            scope.launch {
                busy = true
                val previous = session
                try {
                    if (previous != null) {
                        try { HttpSyncApi().logout(previous) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
                    }
                    withContext(Dispatchers.IO) { store.logout() }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (failure: Exception) { error = failure.message
                } finally { busy = false }
            }
        }) { Text("Sair") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } }
    )
}
