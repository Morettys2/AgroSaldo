package br.joao.agrosaldo.core.util

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

/** Equivalente a `runCatching` que relança CancellationException, preservando o cancelamento cooperativo. */
@Suppress("TooGenericExceptionCaught") // ponto único que vira qualquer falha em Result; o cancelamento é relançado
inline fun <T> tentar(bloco: () -> T): Result<T> = try {
    Result.success(bloco())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Mensagem exibível ao usuário. IllegalArgumentException (vinda de `require` no domínio)
 * já traz texto amigável; demais erros viram [padrao].
 */
fun Throwable.mensagemParaUsuario(padrao: String): String =
    // NumberFormatException estende IllegalArgumentException, mas tem mensagem técnica.
    if (this is IllegalArgumentException && this !is NumberFormatException) message ?: padrao else padrao

private const val ESPERA_INICIAL_MS = 1_000L
private const val ESPERA_MAXIMA_MS = 30_000L
private const val DOBRAS_MAXIMAS = 5L

/**
 * Leitura contínua que se recupera de erro (ex.: falha ao ler o banco).
 *
 * Um `.catch` simples encerraria o Flow e a tela pararia de atualizar para sempre. Aqui,
 * a cada falha, [aoFalhar] é avisado (para a tela mostrar a mensagem) e o Flow é assinado
 * de novo depois de uma espera crescente — 1 s, 2 s, 4 s… até 30 s —, para um erro que
 * persiste não virar um laço apertado. Cancelamento nunca é tratado como erro.
 */
fun <T> Flow<T>.repetirAposErro(aoFalhar: (Throwable) -> Unit): Flow<T> = retryWhen { erro, tentativa ->
    if (erro is CancellationException) return@retryWhen false
    aoFalhar(erro)
    delay(minOf(ESPERA_INICIAL_MS shl tentativa.coerceAtMost(DOBRAS_MAXIMAS).toInt(), ESPERA_MAXIMA_MS))
    true
}
