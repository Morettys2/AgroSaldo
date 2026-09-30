package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.model.Lancamento
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de acesso aos lançamentos — camada Model.
 *
 * Expõe a lista inteira e deixa o filtro por propriedade e por mês para o
 * ViewModel. Com dados em memória isso é o mais simples e direto.
 *
 * Quando o Room entrar, os filtros descem para o SQL (WHERE e SUM no banco)
 * e a interface ganha métodos mais específicos. É a única parte do app que
 * vai precisar mudar.
 */
interface LancamentoRepository {

    fun observarTodos(): Flow<List<Lancamento>>

    suspend fun buscarPorId(id: String): Lancamento?

    suspend fun salvar(lancamento: Lancamento)

    suspend fun excluir(id: String)

    /** Usado ao excluir uma propriedade: os lançamentos dela vão junto (RN02). */
    suspend fun excluirDaPropriedade(propriedadeId: String)
}
