package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.PropriedadeExcluida
import kotlinx.coroutines.flow.Flow

/** Acesso às propriedades do usuário conectado. */
interface PropriedadeRepository {

    fun observarTodas(): Flow<List<Propriedade>>

    suspend fun buscarPorId(id: String): Propriedade?

    suspend fun salvar(propriedade: Propriedade)

    suspend fun excluir(id: String)

    /**
     * Exclui a propriedade e seus lançamentos numa única transação.
     * Retorna o que foi removido (para desfazer) ou null se o id não existe.
     */
    suspend fun excluirComLancamentos(id: String): PropriedadeExcluida?

    /** Reinsere, numa única transação, o que [excluirComLancamentos] removeu. */
    suspend fun restaurar(excluida: PropriedadeExcluida)
}
