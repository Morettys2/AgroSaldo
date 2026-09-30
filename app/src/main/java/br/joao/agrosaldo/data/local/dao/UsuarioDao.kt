package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {

    /**
     * IGNORE, e não REPLACE: se o e-mail já existir, o SQLite simplesmente
     * não insere e o Room devolve -1. O REPLACE apagaria a conta antiga — e,
     * pelo CASCADE, todas as propriedades dela.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserir(usuario: UsuarioEntity): Long

    @Query("SELECT * FROM usuarios WHERE id = :id")
    fun observar(id: String): Flow<UsuarioEntity?>

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun buscarPorId(id: String): UsuarioEntity?

    @Query("SELECT * FROM usuarios WHERE email = :email")
    suspend fun buscarPorEmail(email: String): UsuarioEntity?

    @Query("UPDATE usuarios SET senha_hash = :senhaHash WHERE id = :id")
    suspend fun atualizarSenha(id: String, senhaHash: String)

    /** Propriedades e lançamentos vão junto pelas chaves estrangeiras em CASCADE. */
    @Query("DELETE FROM usuarios WHERE id = :id")
    suspend fun excluir(id: String)

    /**
     * Dá dono às propriedades que não têm (as de antes do login e os
     * exemplos). Devolve quantas foram adotadas.
     */
    @Query("UPDATE propriedades SET usuario_id = :usuarioId WHERE usuario_id IS NULL")
    suspend fun adotarPropriedadesSemDono(usuarioId: String): Int
}
