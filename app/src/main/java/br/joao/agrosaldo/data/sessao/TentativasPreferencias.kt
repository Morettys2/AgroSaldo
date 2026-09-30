package br.joao.agrosaldo.data.sessao

import android.content.Context
import androidx.core.content.edit
import br.joao.agrosaldo.domain.repository.EstadoTentativas
import br.joao.agrosaldo.domain.repository.TentativasRepository

/**
 * Contador de senhas erradas gravado no aparelho, para sobreviver a fechar
 * o app. Fica fora do backup (veja res/xml/data_extraction_rules.xml): é um
 * estado deste aparelho, não algo que deva ir para um celular novo.
 */
class TentativasPreferencias(context: Context) : TentativasRepository {

    private val preferencias =
        context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    override fun ler(): EstadoTentativas = EstadoTentativas(
        falhasSeguidas = preferencias.getInt(CHAVE_FALHAS, 0),
        bloqueadoAte = preferencias.getLong(CHAVE_BLOQUEIO, 0L)
    )

    override fun gravar(estado: EstadoTentativas) {
        preferencias.edit {
            putInt(CHAVE_FALHAS, estado.falhasSeguidas)
            putLong(CHAVE_BLOQUEIO, estado.bloqueadoAte)
        }
    }

    private companion object {
        const val ARQUIVO = "seguranca"
        const val CHAVE_FALHAS = "falhas_seguidas"
        const val CHAVE_BLOQUEIO = "bloqueado_ate"
    }
}
