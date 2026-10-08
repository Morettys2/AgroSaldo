package br.joao.agrosaldo.data.sessao

import android.content.Context
import androidx.core.content.edit
import br.joao.agrosaldo.domain.model.InstanteMonotonico
import br.joao.agrosaldo.domain.repository.EstadoTentativas
import br.joao.agrosaldo.domain.repository.TentativasRepository

/**
 * [TentativasRepository] em SharedPreferences (arquivo "seguranca"). Estado
 * por aparelho, excluído do backup (res/xml/data_extraction_rules.xml).
 */
class TentativasPreferencias(context: Context) : TentativasRepository {

    private val preferencias =
        context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    override fun ler(): EstadoTentativas = EstadoTentativas(
        falhasSeguidas = preferencias.getInt(CHAVE_FALHAS, 0),
        bloqueadoAte = preferencias.getLong(CHAVE_BLOQUEIO, 0L),
        fimMonotonico = lerFimMonotonico()
    )

    override fun gravar(estado: EstadoTentativas) {
        preferencias.edit {
            putInt(CHAVE_FALHAS, estado.falhasSeguidas)
            putLong(CHAVE_BLOQUEIO, estado.bloqueadoAte)
            val fim = estado.fimMonotonico
            if (fim == null) {
                remove(CHAVE_BOOT)
                remove(CHAVE_FIM_MONOTONICO)
            } else {
                putInt(CHAVE_BOOT, fim.boot)
                putLong(CHAVE_FIM_MONOTONICO, fim.desdeOBootMs)
            }
        }
    }

    private fun lerFimMonotonico(): InstanteMonotonico? {
        if (!preferencias.contains(CHAVE_BOOT) || !preferencias.contains(CHAVE_FIM_MONOTONICO)) return null
        return InstanteMonotonico(
            boot = preferencias.getInt(CHAVE_BOOT, 0),
            desdeOBootMs = preferencias.getLong(CHAVE_FIM_MONOTONICO, 0L)
        )
    }

    private companion object {
        const val ARQUIVO = "seguranca"
        const val CHAVE_FALHAS = "falhas_seguidas"
        const val CHAVE_BLOQUEIO = "bloqueado_ate"
        const val CHAVE_BOOT = "bloqueio_boot"
        const val CHAVE_FIM_MONOTONICO = "bloqueio_fim_desde_boot"
    }
}
