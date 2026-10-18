package br.joao.agrosaldo.data.preferencias

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import br.joao.agrosaldo.domain.repository.AjustesAviso
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import kotlinx.coroutines.CoroutineScope

private const val ARQUIVO = "aviso_chuva"

/**
 * [AjustesAvisoRepository] do app: [AjustesAvisoPorConta] em SharedPreferences (arquivo "aviso_chuva").
 *
 * @param escopo ver [AjustesAvisoPorConta]; em produção é o escopo do app.
 * @param aoEntrarEmConta ver [AjustesAvisoPorConta].
 */
class AjustesAvisoPreferencias(
    context: Context,
    sessao: SessaoRepository,
    escopo: CoroutineScope,
    aoEntrarEmConta: (AjustesAviso) -> Unit = {}
) : AjustesAvisoRepository by AjustesAvisoPorConta(
    armazenamento = ArmazenamentoPreferencias(
        context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
    ),
    sessao = sessao,
    aoEntrarEmConta = aoEntrarEmConta,
    escopo = escopo
)

private class ArmazenamentoPreferencias(private val preferencias: SharedPreferences) : ArmazenamentoDeAjustes {

    override fun todos(): Map<String, Any?> = preferencias.all

    override fun editar(gravar: Map<String, Any>, remover: Collection<String>) {
        preferencias.edit {
            remover.forEach { remove(it) }
            gravar.forEach { (chave, valor) ->
                when (valor) {
                    is Boolean -> putBoolean(chave, valor)
                    is Int -> putInt(chave, valor)
                    is Long -> putLong(chave, valor)
                    is Float -> putFloat(chave, valor)
                    is Set<*> -> putStringSet(chave, valor.filterIsInstance<String>().toSet())
                    else -> error("Tipo não suportado na chave $chave: ${valor::class.java.simpleName}")
                }
            }
        }
    }
}
