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
     * IGNORE: e-mail duplicado retorna -1 sem inserir. REPLACE apagaria a conta
     * existente e, pelo CASCADE, todos os seus dados.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserir(usuario: UsuarioEntity): Long

    @Query("SELECT * FROM usuarios WHERE id = :id")
    fun observar(id: String): Flow<UsuarioEntity?>

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun buscarPorId(id: String): UsuarioEntity?

    @Query("SELECT * FROM usuarios WHERE email = :email")
    suspend fun buscarPorEmail(email: String): UsuarioEntity?

    @Query("UPDATE usuarios SET consentimento_em = :em, versao_termo = :versao WHERE id = :id")
    suspend fun registrarConsentimento(id: String, em: Long, versao: Int)

    @Query("UPDATE usuarios SET senha_hash = :senhaHash WHERE id = :id")
    suspend fun atualizarSenha(id: String, senhaHash: String)

    /** Propriedades, lançamentos e previsões são removidos pelo CASCADE. */
    @Query("DELETE FROM usuarios WHERE id = :id")
    suspend fun excluir(id: String)

    /** Atribui a [usuarioId] as propriedades sem dono. Retorna quantas foram adotadas. */
    @Query("UPDATE propriedades SET usuario_id = :usuarioId WHERE usuario_id IS NULL")
    suspend fun adotarPropriedadesSemDono(usuarioId: String): Int
}
