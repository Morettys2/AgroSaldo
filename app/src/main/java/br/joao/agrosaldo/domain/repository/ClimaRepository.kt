package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.clima.Previsao
import br.joao.agrosaldo.domain.model.Propriedade
import kotlinx.coroutines.flow.Flow

sealed interface ResultadoAtualizacao {
    data class Sucesso(val previsao: Previsao) : ResultadoAtualizacao
    /** Sem internet ou servidor fora do ar. A previsão guardada continua valendo. */
    data object SemConexao : ResultadoAtualizacao
    /** O servidor respondeu algo que não entendemos. */
    data class Falha(val motivo: String) : ResultadoAtualizacao
}

/**
 * Previsão do tempo de uma propriedade.
 *
 * Funciona como "cache primeiro": a tela sempre mostra o que está guardado
 * no banco, e a internet só serve para trocar o guardado por um mais novo.
 * Sem sinal no sítio, o app continua mostrando a última previsão conhecida.
 */
interface ClimaRepository {

    /** A previsão guardada para as coordenadas atuais da propriedade (ou null). */
    fun observar(propriedade: Propriedade): Flow<Previsao?>

    suspend fun emCache(propriedade: Propriedade): Previsao?

    /** Busca na internet e guarda. */
    suspend fun atualizar(propriedade: Propriedade): ResultadoAtualizacao

    /** Só busca se a guardada tiver mais de 1 hora. Devolve null quando não precisou. */
    suspend fun atualizarSeNecessario(propriedade: Propriedade): ResultadoAtualizacao?
}
