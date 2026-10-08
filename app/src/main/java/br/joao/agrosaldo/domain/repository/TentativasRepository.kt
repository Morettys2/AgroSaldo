package br.joao.agrosaldo.domain.repository

/** Falhas de senha consecutivas e fim do bloqueio (epoch ms; 0 = sem bloqueio). */
data class EstadoTentativas(
    val falhasSeguidas: Int = 0,
    val bloqueadoAte: Long = 0L
)

/**
 * Persistência do contador de tentativas. Deve sobreviver ao fechamento do
 * app; caso contrário reiniciar o processo zeraria o bloqueio.
 */
interface TentativasRepository {
    fun ler(): EstadoTentativas
    fun gravar(estado: EstadoTentativas)
}
