package br.joao.agrosaldo.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Em que thread cada tipo de trabalho roda. Injetado em vez de escrever `Dispatchers.Default`
 * no meio do código: os testes trocam tudo por um dispatcher de teste e o trabalho passa a
 * rodar de forma previsível, sem threads de verdade.
 */
interface Despachantes {

    /** Trabalho de CPU, como o hash da senha (PBKDF2) e a montagem de listas grandes. */
    val calculo: CoroutineDispatcher

    /** Trabalho que espera disco ou rede. */
    val io: CoroutineDispatcher
}

/** Os dispatchers de verdade, usados pelo app. */
object DespachantesDoSistema : Despachantes {
    override val calculo: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
}
