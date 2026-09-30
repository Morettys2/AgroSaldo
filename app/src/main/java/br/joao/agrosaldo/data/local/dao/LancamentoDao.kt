package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Lançamento não tem coluna de dono: ele pertence a uma propriedade, e a
 * propriedade pertence ao usuário. Por isso as consultas fazem JOIN com
 * propriedades para saber de quem é cada lançamento.
 *
 * Como a consulta envolve as duas tabelas, o Room observa as duas: se uma
 * propriedade for excluída, a lista de lançamentos se atualiza sozinha.
 */
@Dao
interface LancamentoDao {

    /** Mais recentes primeiro. O índice em "data" deixa essa ordenação barata. */
    @Query(
        """
        SELECT lancamentos.* FROM lancamentos
        INNER JOIN propriedades ON propriedades.id = lancamentos.propriedade_id
        WHERE propriedades.usuario_id = :usuarioId
        ORDER BY lancamentos.data DESC, lancamentos.atualizado_em DESC
        """
    )
    fun observarDoUsuario(usuarioId: String): Flow<List<LancamentoEntity>>

    @Query(
        """
        SELECT lancamentos.* FROM lancamentos
        INNER JOIN propriedades ON propriedades.id = lancamentos.propriedade_id
        WHERE lancamentos.id = :id AND propriedades.usuario_id = :usuarioId
        """
    )
    suspend fun buscarPorId(id: String, usuarioId: String): LancamentoEntity?

    /** Impede gravar um lançamento numa propriedade de outra conta. */
    @Query("SELECT EXISTS(SELECT 1 FROM propriedades WHERE id = :propriedadeId AND usuario_id = :usuarioId)")
    suspend fun propriedadeEhDoUsuario(propriedadeId: String, usuarioId: String): Boolean

    @Upsert
    suspend fun salvar(lancamento: LancamentoEntity)

    @Query(
        """
        DELETE FROM lancamentos
        WHERE id = :id
          AND propriedade_id IN (SELECT id FROM propriedades WHERE usuario_id = :usuarioId)
        """
    )
    suspend fun excluir(id: String, usuarioId: String)

    /**
     * O CASCADE da chave estrangeira já faz isso quando a propriedade é
     * excluída. O método existe para cumprir o contrato do repositório — e
     * deixar a intenção explícita no código, sem depender só da regra do banco.
     */
    @Query(
        """
        DELETE FROM lancamentos
        WHERE propriedade_id = :propriedadeId
          AND propriedade_id IN (SELECT id FROM propriedades WHERE usuario_id = :usuarioId)
        """
    )
    suspend fun excluirDaPropriedade(propriedadeId: String, usuarioId: String)

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
