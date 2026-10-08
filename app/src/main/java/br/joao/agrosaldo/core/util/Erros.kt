package br.joao.agrosaldo.core.util

import kotlin.coroutines.cancellation.CancellationException

/** Equivalente a `runCatching` que relança CancellationException, preservando o cancelamento cooperativo. */
inline fun <T> tentar(bloco: () -> T): Result<T> =
    try {
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
