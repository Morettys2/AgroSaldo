package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO (`@Dao`) da tabela `propriedades`. Consultas e escritas filtram por `usuario_id`, o que
 * isola os dados de cada conta; a exceção é [existeSemSerDoUsuario], a checagem de posse.
 */
@Dao
interface PropriedadeDao {

    /**
     * Observa as propriedades do usuário (`Flow`). Sem ORDER BY: o COLLATE do SQLite não trata
     * acentos, então o repositório ordena em pt-BR.
     */
    @Query("SELECT * FROM propriedades WHERE usuario_id = :usuarioId")
    fun observarDoUsuario(usuarioId: String): Flow<List<PropriedadeEntity>>

    /** Lê uma propriedade do usuário pelo id. */
    @Query("SELECT * FROM propriedades WHERE id = :id AND usuario_id = :usuarioId")
    suspend fun buscarPorId(id: String, usuarioId: String): PropriedadeEntity?

    /**
     * true se a propriedade [id] existe e é de outra conta. Checagem de posse antes do upsert, que
     * casa só pelo id.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM propriedades
            WHERE id = :id AND usuario_id <> :usuarioId
        )
        """
    )
    suspend fun existeSemSerDoUsuario(id: String, usuarioId: String): Boolean

    /**
     * Insere ou atualiza (`@Upsert`). Não trocar por `@Insert(onConflict = REPLACE)`: no SQLite o
     * REPLACE faz DELETE + INSERT, o que dispara o CASCADE e apaga os lançamentos e a previsão.
     */
    @Upsert
    suspend fun salvar(propriedade: PropriedadeEntity)

    /** Exclui a propriedade do usuário; lançamentos e previsão saem pelo ON DELETE CASCADE. */
    @Query("DELETE FROM propriedades WHERE id = :id AND usuario_id = :usuarioId")
    suspend fun excluir(id: String, usuarioId: String)
}
