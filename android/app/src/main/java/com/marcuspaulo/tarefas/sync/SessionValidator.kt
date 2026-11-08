package com.marcuspaulo.tarefas.sync

import java.io.IOException

/** Confere a sessão em primeiro plano, mesmo quando a fila de sincronização está aguardando reenvio. */
class SessionValidator(
    private val store: SessionStore,
    private val validate: suspend (Session) -> Boolean
) {
    suspend fun check() {
        val session = store.session.value?.takeUnless { it.expired } ?: return
        try {
            store.updateVerification(session, validate(session))
        } catch (error: ApiException) {
            if (error.status == 401) store.expire(session)
        } catch (_: IOException) {
            // Sem resposta do servidor, o funcionamento offline continua disponível.
        }
    }
}
