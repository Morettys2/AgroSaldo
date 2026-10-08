package br.joao.agrosaldo.core.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Como o `runCatching` do Kotlin, mas sem engolir o cancelamento.
 *
 * Quando a tela fecha, o viewModelScope cancela as coroutines dela lançando
 * CancellationException. O runCatching comum pegaria essa exceção e o
 * trabalho "cancelado" continuaria rodando. Aqui ela passa direto.
 */
inline fun <T> tentar(bloco: () -> T): Result<T> =
    try {
        Result.success(bloco())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/**
 * Mensagem para mostrar ao produtor quando algo dá errado ao gravar.
 *
 * IllegalArgumentException vem das regras do domínio (o `require` dos
 * modelos e repositórios), que já falam português de gente. Qualquer outro
 * erro (banco, sessão) vira uma frase genérica — mensagem técnica não ajuda
 * quem está usando.
 */
fun Throwable.mensagemParaUsuario(padrao: String): String =
    // NumberFormatException também é uma IllegalArgumentException, mas a
    // mensagem dela é técnica ("For input string...").
    if (this is IllegalArgumentException && this !is NumberFormatException) message ?: padrao else padrao
