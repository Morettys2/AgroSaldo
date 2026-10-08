package br.joao.agrosaldo.data.preferencias

import android.content.Context
import androidx.core.content.edit
import br.joao.agrosaldo.domain.repository.PrivacidadeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [PrivacidadeRepository] em SharedPreferences (arquivo "privacidade"). */
class PrivacidadePreferencias(context: Context) : PrivacidadeRepository {

    private val preferencias =
        context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    private val _valoresOcultos = MutableStateFlow(preferencias.getBoolean(CHAVE, false))
    override val valoresOcultos: StateFlow<Boolean> = _valoresOcultos.asStateFlow()

    override fun definir(ocultos: Boolean) {
        preferencias.edit { putBoolean(CHAVE, ocultos) }
        _valoresOcultos.value = ocultos
    }

    private companion object {
        const val ARQUIVO = "privacidade"
        const val CHAVE = "valores_ocultos"
    }
}
