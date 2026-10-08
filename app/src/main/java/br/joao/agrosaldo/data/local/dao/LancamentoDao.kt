package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import kotlinx.coroutines.flow.Flow

/**
 * `lancamentos` não tem coluna de dono: a posse é derivada via JOIN com
 * `propriedades.usuario_id`. Os Flows observam as duas tabelas.
 */
@Dao
interface LancamentoDao {

    /** Ordenado por data e depois por última gravação, mais recentes primeiro. */
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

    /** Usado antes de gravar para impedir lançamento em propriedade de outra conta. */
    @Query("SELECT EXISTS(SELECT 1 FROM propriedades WHERE id = :propriedadeId AND usuario_id = :usuarioId)")
    suspend fun propriedadeEhDoUsuario(propriedadeId: String, usuarioId: String): Boolean

    /**
     * true se o lançamento [id] existe numa propriedade que não é de [usuarioId]. Usado antes
     * do upsert, que casa só pelo id.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM lancamentos
            WHERE id = :id
              AND propriedade_id NOT IN (SELECT id FROM propriedades WHERE usuario_id = :usuarioId)
        )
        """
    )
    suspend fun existeSemSerDoUsuario(id: String, usuarioId: String): Boolean

    @Upsert
    suspend fun salvar(lancamento: LancamentoEntity)

    /** Usado ao desfazer a exclusão de uma propriedade. */
    @Upsert
    suspend fun salvarTodos(lancamentos: List<LancamentoEntity>)

    /** Snapshot dos lançamentos antes da exclusão da propriedade (para desfazer). */
    @Query(
        """
        SELECT lancamentos.* FROM lancamentos
        INNER JOIN propriedades ON propriedades.id = lancamentos.propriedade_id
        WHERE lancamentos.propriedade_id = :propriedadeId AND propriedades.usuario_id = :usuarioId
        """
    )
    suspend fun daPropriedade(propriedadeId: String, usuarioId: String): List<LancamentoEntity>

    @Query(
        """
        DELETE FROM lancamentos
        WHERE id = :id
          AND propriedade_id IN (SELECT id FROM propriedades WHERE usuario_id = :usuarioId)
        """
    )
    suspend fun excluir(id: String, usuarioId: String)

    /**
     * Redundante com o ON DELETE CASCADE; chamado explicitamente na mesma
     * transação para não depender só da FK.
     */
    @Query(
        """
        DELETE FROM lancamentos
        WHERE propriedade_id = :propriedadeId
          AND propriedade_id IN (SELECT id FROM propriedades WHERE usuario_id = :usuarioId)
        """
    )
    suspend fun excluirDaPropriedade(propriedadeId: String, usuarioId: String)
}
