package br.joao.agrosaldo.domain.repository

/** Quantas senhas erradas seguidas e até quando a entrada está travada (em milissegundos). */
data class EstadoTentativas(
    val falhasSeguidas: Int = 0,
    val bloqueadoAte: Long = 0L
)

/**
 * Onde fica guardado o contador de senhas erradas.
 *
 * Precisa ser gravado, e não só ficar em memória: se fechar o app zerasse
 * o contador, bastaria fechar e abrir para tentar de novo sem espera.
 */
interface TentativasRepository {
    fun ler(): EstadoTentativas
    fun gravar(estado: EstadoTentativas)
}
