package br.joao.agrosaldo.data.sessao

import android.content.Context
import androidx.core.content.edit
import br.joao.agrosaldo.domain.repository.SessaoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sessão que sobrevive ao app fechar: o produtor entra uma vez e não
 * precisa digitar a senha toda vez que abrir o app.
 *
 * Guarda só o id do usuário, num arquivo de preferências privado do app —
 * nenhum outro app lê. Senha e hash nunca passam por aqui.
 *
 * SharedPreferences e não DataStore: para um único valor, lido uma vez ao
 * abrir o app, ele resolve sem biblioteca nova. A interface
 * [SessaoRepository] deixa trocar pelo DataStore depois sem mexer em mais nada.
 */
class SessaoPreferencias(context: Context) : SessaoRepository {

    private val preferencias =
        context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    // Lido de forma síncrona, uma vez: assim, na hora de desenhar a primeira
    // tela, o app já sabe se abre no login ou no início, sem piscar.
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
