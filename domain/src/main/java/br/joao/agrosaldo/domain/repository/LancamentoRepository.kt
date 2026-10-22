package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.model.Lancamento
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de acesso aos lançamentos do usuário conectado; a implementação com Room fica no app.
 * Expõe a lista completa; os filtros por propriedade e período ficam no ViewModel.
 */
interface LancamentoRepository {

    /** Observa todos os lançamentos do usuário. */
    fun observarTodos(): Flow<List<Lancamento>>

    /** Lê um lançamento do usuário pelo id. */
    suspend fun buscarPorId(id: String): Lancamento?

    /** Insere ou atualiza o lançamento. */
    suspend fun salvar(lancamento: Lancamento)

    /**
     * Exclui o lançamento e devolve o registro apagado (ou null se não existia). A leitura e a
     * exclusão rodam na mesma transação, então o valor devolvido é exatamente o que saiu do banco.
     */
    suspend fun excluir(id: String): Lancamento?
}
