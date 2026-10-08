package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Consultas e escritas filtram por `usuario_id` para isolar os dados de cada
 * conta. Exceção: [existeSemSerDoUsuario], usado na checagem de posse.
 */
@Dao
interface PropriedadeDao {

    /** Ordenado por nome sem diferenciar maiúsculas (NOCASE cobre só ASCII). */
    @Query("SELECT * FROM propriedades WHERE usuario_id = :usuarioId ORDER BY nome COLLATE NOCASE")
    fun observarDoUsuario(usuarioId: String): Flow<List<PropriedadeEntity>>

    @Query("SELECT * FROM propriedades WHERE id = :id AND usuario_id = :usuarioId")
    suspend fun buscarPorId(id: String, usuarioId: String): PropriedadeEntity?

    /**
     * true se a propriedade [id] existe e não é de [usuarioId]: é de outra conta ou ainda
     * não tem dono (só a criação da conta adota essas).
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM propriedades
            WHERE id = :id AND (usuario_id IS NULL OR usuario_id <> :usuarioId)
        )
        """
    )
    suspend fun existeSemSerDoUsuario(id: String, usuarioId: String): Boolean

    /**
     * Não trocar por @Insert(onConflict = REPLACE): o REPLACE do SQLite faz
     * DELETE + INSERT, o que dispara o CASCADE e apaga os lançamentos e a
     * previsão da propriedade. @Upsert faz UPDATE (coberto por teste).
     */
    @Upsert
    suspend fun salvar(propriedade: PropriedadeEntity)

    @Query("DELETE FROM propriedades WHERE id = :id AND usuario_id = :usuarioId")
    suspend fun excluir(id: String, usuarioId: String)

    @Query("SELECT COUNT(*) FROM propriedades WHERE usuario_id = :usuarioId")
    suspend fun contarDoUsuario(usuarioId: String): Int
}
