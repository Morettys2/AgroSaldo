package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO (`@Dao`) da tabela `lancamentos`. A tabela não tem coluna de dono: a posse vem do JOIN com
 * `propriedades.usuario_id`, e os `Flow` observam as duas tabelas.
 */
@Dao
interface LancamentoDao {

    /** Observa os lançamentos do usuário (`Flow`), mais recentes primeiro: por data e depois pela última gravação. */
    @Query(
        """
        SELECT lancamentos.* FROM lancamentos
        INNER JOIN propriedades ON propriedades.id = lancamentos.propriedade_id
        WHERE propriedades.usuario_id = :usuarioId
        ORDER BY lancamentos.data DESC, lancamentos.atualizado_em DESC
        """
    )
    fun observarDoUsuario(usuarioId: String): Flow<List<LancamentoEntity>>

    /** Lê um lançamento do usuário pelo id. */
    @Query(
        """
        SELECT lancamentos.* FROM lancamentos
        INNER JOIN propriedades ON propriedades.id = lancamentos.propriedade_id
        WHERE lancamentos.id = :id AND propriedades.usuario_id = :usuarioId
        """
    )
    suspend fun buscarPorId(id: String, usuarioId: String): LancamentoEntity?

    /** true se a propriedade pertence ao usuário; conferido antes de gravar um lançamento nela. */
    @Query("SELECT EXISTS(SELECT 1 FROM propriedades WHERE id = :propriedadeId AND usuario_id = :usuarioId)")
    suspend fun propriedadeEhDoUsuario(propriedadeId: String, usuarioId: String): Boolean

    /**
     * true se o lançamento [id] existe numa propriedade que não é de [usuarioId]. Conferido antes
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

    /** Insere ou atualiza um lançamento (`@Upsert`, pela chave primária `id`). */
    @Upsert
    suspend fun salvar(lancamento: LancamentoEntity)

    /** Insere ou atualiza vários lançamentos; usado ao restaurar uma propriedade excluída. */
    @Upsert
    suspend fun salvarTodos(lancamentos: List<LancamentoEntity>)

    /** Lista os lançamentos de uma propriedade do usuário (lidos antes de excluí-la, para o desfazer). */
    @Query(
        """
        SELECT lancamentos.* FROM lancamentos
        INNER JOIN propriedades ON propriedades.id = lancamentos.propriedade_id
        WHERE lancamentos.propriedade_id = :propriedadeId AND propriedades.usuario_id = :usuarioId
        """
    )
    suspend fun daPropriedade(propriedadeId: String, usuarioId: String): List<LancamentoEntity>

    /** Exclui um lançamento, só se a propriedade dele for do usuário. */
    @Query(
        """
        DELETE FROM lancamentos
        WHERE id = :id
          AND propriedade_id IN (SELECT id FROM propriedades WHERE usuario_id = :usuarioId)
        """
    )
    suspend fun excluir(id: String, usuarioId: String)

    /**
     * Exclui os lançamentos de uma propriedade do usuário. É redundante com o ON DELETE CASCADE,
     * mas roda explicitamente na mesma transação para não depender só da chave estrangeira.
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
