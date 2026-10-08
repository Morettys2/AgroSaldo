package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.model.Lancamento
import kotlinx.coroutines.flow.Flow

/**
 * Acesso aos lançamentos do usuário conectado.
 *
 * Expõe a lista completa; filtros por propriedade e período ficam no ViewModel.
 */
interface LancamentoRepository {

    fun observarTodos(): Flow<List<Lancamento>>

    suspend fun buscarPorId(id: String): Lancamento?

    suspend fun salvar(lancamento: Lancamento)

    suspend fun excluir(id: String)
}
