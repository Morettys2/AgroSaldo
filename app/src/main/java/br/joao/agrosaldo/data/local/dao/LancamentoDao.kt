package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LancamentoDao {

    /** Mais recentes primeiro. O índice em "data" deixa essa ordenação barata. */
    @Query("SELECT * FROM lancamentos ORDER BY data DESC, atualizado_em DESC")
    fun observarTodos(): Flow<List<LancamentoEntity>>

    @Query("SELECT * FROM lancamentos WHERE id = :id")
    suspend fun buscarPorId(id: String): LancamentoEntity?

    @Upsert
    suspend fun salvar(lancamento: LancamentoEntity)

    @Query("DELETE FROM lancamentos WHERE id = :id")
    suspend fun excluir(id: String)

    /**
     * O CASCADE da chave estrangeira já faz isso quando a propriedade é
     * excluída. O método existe para cumprir o contrato do repositório — e
     * deixar a intenção explícita no código, sem depender só da regra do banco.
     */
    @Query("DELETE FROM lancamentos WHERE propriedade_id = :propriedadeId")
    suspend fun excluirDaPropriedade(propriedadeId: String)

    /**
     * Saldo de um período calculado pelo próprio SQLite (RN03).
     *
     * Ainda não é usado pelas telas — hoje os ViewModels somam em Kotlin, o que
     * é rápido o bastante para o volume de um produtor. Fica pronto para quando
     * o histórico crescer: aí a soma sai do Kotlin e vem pronta do banco.
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN tipo = 'RECEITA' THEN valor_centavos
                                 ELSE -valor_centavos END), 0)
        FROM lancamentos
        WHERE propriedade_id = :propriedadeId
          AND data BETWEEN :inicioEpochDay AND :fimEpochDay
        """
    )
    suspend fun saldoDoPeriodo(propriedadeId: String, inicioEpochDay: Long, fimEpochDay: Long): Long
}
