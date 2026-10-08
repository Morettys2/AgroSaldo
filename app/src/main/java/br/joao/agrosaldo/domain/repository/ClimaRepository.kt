package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.clima.Previsao
import br.joao.agrosaldo.domain.model.Propriedade
import kotlinx.coroutines.flow.Flow

sealed interface ResultadoAtualizacao {
    data class Sucesso(val previsao: Previsao) : ResultadoAtualizacao
    /** Falha de rede ou servidor indisponível; o cache permanece. */
    data object SemConexao : ResultadoAtualizacao
    /** Resposta inesperada ou inválida da API. */
    data class Falha(val motivo: String) : ResultadoAtualizacao
}

/**
 * Previsão do tempo por propriedade, cache-first: a UI observa o cache e a
 * rede apenas substitui o valor guardado. Sem conexão, mantém a última previsão.
 */
interface ClimaRepository {

    /** Previsão em cache para as coordenadas atuais da propriedade, ou null. */
    fun observar(propriedade: Propriedade): Flow<Previsao?>

    suspend fun emCache(propriedade: Propriedade): Previsao?

    /** Busca na API e grava no cache, ignorando a validade. */
    suspend fun atualizar(propriedade: Propriedade): ResultadoAtualizacao

    /** Atualiza só se o cache venceu ([br.joao.agrosaldo.domain.clima.PoliticaDeAtualizacao]); null se não buscou. */
    suspend fun atualizarSeNecessario(propriedade: Propriedade): ResultadoAtualizacao?
}
