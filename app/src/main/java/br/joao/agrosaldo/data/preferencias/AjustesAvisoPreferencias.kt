package br.joao.agrosaldo.data.preferencias

import android.content.Context
import androidx.core.content.edit
import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.repository.AjustesAviso
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * [AjustesAvisoRepository] em SharedPreferences (arquivo "aviso_chuva").
 * O último aviso de cada propriedade fica em chaves `ultimo_aviso_<id>` (epoch ms).
 */
class AjustesAvisoPreferencias(context: Context) : AjustesAvisoRepository {

    private val preferencias =
        context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    private val _ajustes = MutableStateFlow(ler())
    override val ajustes: StateFlow<AjustesAviso> = _ajustes.asStateFlow()

    private fun ler(): AjustesAviso {
        val padrao = LimitesChuva()
        // Valores fora da faixa fazem o init de LimitesChuva lançar; volta ao padrão.
        val limites = runCatching {
            LimitesChuva(
                probabilidadeMinima = preferencias.getInt(CHAVE_PROBABILIDADE, padrao.probabilidadeMinima),
                chuvaMinimaMm = preferencias.getFloat(CHAVE_CHUVA, padrao.chuvaMinimaMm.toFloat()).toDouble(),
                janelaHoras = preferencias.getInt(CHAVE_JANELA, padrao.janelaHoras)
            )
        }.getOrDefault(padrao)
        return AjustesAviso(
            ativo = preferencias.getBoolean(CHAVE_ATIVO, true),
            limites = limites,
            // Cópia defensiva: o Set retornado por getStringSet não deve ser modificado.
            propriedadesSemAviso = preferencias.getStringSet(CHAVE_SEM_AVISO, null)?.toSet().orEmpty()
        )
    }

    override fun salvar(ajustes: AjustesAviso) {
        preferencias.edit {
            putBoolean(CHAVE_ATIVO, ajustes.ativo)
            putInt(CHAVE_PROBABILIDADE, ajustes.limites.probabilidadeMinima)
            putFloat(CHAVE_CHUVA, ajustes.limites.chuvaMinimaMm.toFloat())
            putInt(CHAVE_JANELA, ajustes.limites.janelaHoras)
            putStringSet(CHAVE_SEM_AVISO, ajustes.propriedadesSemAviso.toSet())
        }
        _ajustes.value = ajustes
    }

    override fun ultimoAvisoEm(propriedadeId: String): Instant? {
        val ms = preferencias.getLong(PREFIXO_ULTIMO + propriedadeId, 0L)
        return if (ms == 0L) null else Instant.ofEpochMilli(ms)
    }

    override fun registrarAviso(propriedadeId: String, em: Instant) {
        preferencias.edit { putLong(PREFIXO_ULTIMO + propriedadeId, em.toEpochMilli()) }
    }

    private companion object {
        const val ARQUIVO = "aviso_chuva"
        const val CHAVE_ATIVO = "ativo"
        const val CHAVE_PROBABILIDADE = "probabilidade_minima"
        const val CHAVE_CHUVA = "chuva_minima_mm"
        const val CHAVE_JANELA = "janela_horas"
        const val CHAVE_SEM_AVISO = "propriedades_sem_aviso"
        const val PREFIXO_ULTIMO = "ultimo_aviso_"
    }
}
