package br.joao.agrosaldo.data.sessao

import android.content.Context
import androidx.core.content.edit
import br.joao.agrosaldo.domain.repository.BloqueioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fica no mesmo arquivo do contador de senhas erradas, que já está fora do backup. */
class BloqueioPreferencias(context: Context) : BloqueioRepository {

    private val preferencias =
        context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    private val _ativo = MutableStateFlow(preferencias.getBoolean(CHAVE, false))
    override val ativo: StateFlow<Boolean> = _ativo.asStateFlow()

    override fun definir(ativo: Boolean) {
        preferencias.edit { putBoolean(CHAVE, ativo) }
        _ativo.value = ativo
    }

    private companion object {
        const val ARQUIVO = "seguranca"
        const val CHAVE = "bloqueio_ativo"
    }
}
