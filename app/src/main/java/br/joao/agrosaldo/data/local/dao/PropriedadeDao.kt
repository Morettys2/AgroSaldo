package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Consultas e escritas filtram por `usuario_id` para isolar os dados de cada
 * conta. Exceção: [donoDe], usado na checagem de posse.
 */
@Dao
interface PropriedadeDao {

    /** Ordenado por nome sem diferenciar maiúsculas (NOCASE cobre só ASCII). */
    @Query("SELECT * FROM propriedades WHERE usuario_id = :usuarioId ORDER BY nome COLLATE NOCASE")
    fun observarDoUsuario(usuarioId: String): Flow<List<PropriedadeEntity>>

    @Query("SELECT * FROM propriedades WHERE id = :id AND usuario_id = :usuarioId")
    suspend fun buscarPorId(id: String, usuarioId: String): PropriedadeEntity?

    /** Dono atual, ou null se a propriedade não existe ou ainda não tem dono. */
    @Query("SELECT usuario_id FROM propriedades WHERE id = :id")
    suspend fun donoDe(id: String): String?

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
