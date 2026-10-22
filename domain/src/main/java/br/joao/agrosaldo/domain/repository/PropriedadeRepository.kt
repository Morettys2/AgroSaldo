package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.PropriedadeExcluida
import kotlinx.coroutines.flow.Flow

/** Contrato de acesso às propriedades do usuário conectado; a implementação com Room fica no app. */
interface PropriedadeRepository {

    /** Observa as propriedades do usuário, em ordem alfabética. */
    fun observarTodas(): Flow<List<Propriedade>>

    /** Lê uma propriedade do usuário pelo id. */
    suspend fun buscarPorId(id: String): Propriedade?

    /** Insere ou atualiza a propriedade. */
    suspend fun salvar(propriedade: Propriedade)

    /**
     * Exclui a propriedade e seus lançamentos numa única transação.
     * Devolve o que foi removido (para desfazer) ou null se o id não existe.
     */
    suspend fun excluirComLancamentos(id: String): PropriedadeExcluida?

    /** Reinsere, numa única transação, o que [excluirComLancamentos] removeu. */
    suspend fun restaurar(excluida: PropriedadeExcluida)
}
