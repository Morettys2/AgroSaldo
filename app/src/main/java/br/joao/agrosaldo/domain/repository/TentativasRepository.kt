package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.model.InstanteMonotonico

/**
 * Falhas de senha consecutivas e fim do bloqueio.
 *
 * @property bloqueadoAte fim do bloqueio em epoch ms (0 = sem bloqueio); vale após reinício do aparelho.
 * @property fimMonotonico fim do bloqueio no relógio monotônico do boot em que foi gravado;
 *   null se o relógio monotônico não estava disponível.
 */
data class EstadoTentativas(val falhasSeguidas: Int = 0, val bloqueadoAte: Long = 0L, val fimMonotonico: InstanteMonotonico? = null)

/**
 * Persistência dos contadores de tentativas, um por [chave] (resumo do e-mail; ver
 * [br.joao.agrosaldo.domain.conta.ContaService]). Deve sobreviver ao fechamento do
 * app; caso contrário reiniciar o processo zeraria o bloqueio.
 */
interface TentativasRepository {
    fun ler(chave: String): EstadoTentativas

    /** Gravar o estado inicial, `EstadoTentativas()`, apaga o registro da chave. */
    fun gravar(chave: String, estado: EstadoTentativas)
}
