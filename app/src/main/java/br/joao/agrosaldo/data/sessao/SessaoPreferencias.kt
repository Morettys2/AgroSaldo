package br.joao.agrosaldo.data.sessao

import android.content.Context
import androidx.core.content.edit
import br.joao.agrosaldo.domain.repository.SessaoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sessão persistente em SharedPreferences privadas (arquivo "sessao").
 * Guarda apenas o id do usuário; nenhuma credencial.
 */
class SessaoPreferencias(context: Context) : SessaoRepository {

    private val preferencias =
        context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    // Leitura síncrona na construção: a primeira tela já sabe se há sessão.
    private val _usuarioId = MutableStateFlow(preferencias.getString(CHAVE_USUARIO, null))
    override val usuarioId: StateFlow<String?> = _usuarioId.asStateFlow()

    override fun iniciar(usuarioId: String) {
        preferencias.edit { putString(CHAVE_USUARIO, usuarioId) }
        _usuarioId.value = usuarioId
    }

    override fun encerrar() {
        preferencias.edit { remove(CHAVE_USUARIO) }
        _usuarioId.value = null
    }

    private companion object {
        const val ARQUIVO = "sessao"
        const val CHAVE_USUARIO = "usuario_id"
    }
}
