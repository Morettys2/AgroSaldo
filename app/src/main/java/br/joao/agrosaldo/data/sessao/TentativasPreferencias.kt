package br.joao.agrosaldo.data.sessao

import android.content.Context
import androidx.core.content.edit
import br.joao.agrosaldo.domain.model.InstanteMonotonico
import br.joao.agrosaldo.domain.repository.EstadoTentativas
import br.joao.agrosaldo.domain.repository.TentativasRepository

/**
 * [TentativasRepository] em SharedPreferences (arquivo "seguranca"): cada contador grava
 * os campos de [CAMPOS] com o sufixo `_<chave>`. Estado por aparelho, excluído do backup
 * (res/xml/data_extraction_rules.xml).
 */
class TentativasPreferencias(context: Context) : TentativasRepository {

    private val preferencias =
        context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    init {
        // Contador único das versões anteriores (campos sem sufixo), substituído pelos contadores por e-mail.
        if (preferencias.contains(FALHAS)) preferencias.edit { CAMPOS.forEach { remove(it) } }
    }

    override fun ler(chave: String): EstadoTentativas = EstadoTentativas(
        falhasSeguidas = preferencias.getInt(campo(FALHAS, chave), 0),
        bloqueadoAte = preferencias.getLong(campo(BLOQUEIO, chave), 0L),
        fimMonotonico = lerFimMonotonico(chave)
    )

    override fun gravar(chave: String, estado: EstadoTentativas) {
        preferencias.edit {
            if (estado == EstadoTentativas()) {
                CAMPOS.forEach { remove(campo(it, chave)) }
            } else {
                putInt(campo(FALHAS, chave), estado.falhasSeguidas)
                putLong(campo(BLOQUEIO, chave), estado.bloqueadoAte)
                val fim = estado.fimMonotonico
                if (fim == null) {
                    remove(campo(BOOT, chave))
                    remove(campo(FIM_MONOTONICO, chave))
                } else {
                    putInt(campo(BOOT, chave), fim.boot)
                    putLong(campo(FIM_MONOTONICO, chave), fim.desdeOBootMs)
                }
            }
        }
    }

    private fun lerFimMonotonico(chave: String): InstanteMonotonico? {
        val boot = campo(BOOT, chave)
        val fim = campo(FIM_MONOTONICO, chave)
        if (!preferencias.contains(boot) || !preferencias.contains(fim)) return null
        return InstanteMonotonico(
            boot = preferencias.getInt(boot, 0),
            desdeOBootMs = preferencias.getLong(fim, 0L)
        )
    }

    private fun campo(nome: String, chave: String) = "${nome}_$chave"

    private companion object {
        const val ARQUIVO = "seguranca"
        const val FALHAS = "falhas_seguidas"
        const val BLOQUEIO = "bloqueado_ate"
        const val BOOT = "bloqueio_boot"
        const val FIM_MONOTONICO = "bloqueio_fim_desde_boot"
        val CAMPOS = listOf(FALHAS, BLOQUEIO, BOOT, FIM_MONOTONICO)
    }
}
