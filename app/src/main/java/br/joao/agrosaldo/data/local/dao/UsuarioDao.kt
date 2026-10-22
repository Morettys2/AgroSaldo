package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity
import kotlinx.coroutines.flow.Flow

/** DAO (`@Dao`) da tabela `usuarios`: o Room gera a implementação a partir das anotações. */
@Dao
interface UsuarioDao {

    /**
     * Insere a conta e devolve o rowid, ou -1 se o e-mail já existe (`OnConflictStrategy.IGNORE`).
     * REPLACE apagaria a conta existente e, pelo CASCADE, todos os dados dela.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserir(usuario: UsuarioEntity): Long

    /** Observa a conta pelo id: o `Flow` emite de novo a cada mudança na tabela. */
    @Query("SELECT * FROM usuarios WHERE id = :id")
    fun observar(id: String): Flow<UsuarioEntity?>

    /** Lê a conta pelo id uma vez. */
    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun buscarPorId(id: String): UsuarioEntity?

    /** Busca a conta pelo e-mail já normalizado (login e cadastro). */
    @Query("SELECT * FROM usuarios WHERE email = :email")
    suspend fun buscarPorEmail(email: String): UsuarioEntity?

    /** Grava o instante e a versão do termo de privacidade aceito. */
    @Query("UPDATE usuarios SET consentimento_em = :em, versao_termo = :versao WHERE id = :id")
    suspend fun registrarConsentimento(id: String, em: Long, versao: Int)

    /** Substitui o hash da senha. */
    @Query("UPDATE usuarios SET senha_hash = :senhaHash WHERE id = :id")
    suspend fun atualizarSenha(id: String, senhaHash: String)

    /** Exclui a conta; propriedades, lançamentos e previsões saem pelo ON DELETE CASCADE. */
    @Query("DELETE FROM usuarios WHERE id = :id")
    suspend fun excluir(id: String)

    /** Atribui a [usuarioId] as propriedades sem dono (`usuario_id IS NULL`); devolve quantas linhas mudaram. */
    @Query("UPDATE propriedades SET usuario_id = :usuarioId WHERE usuario_id IS NULL")
    suspend fun adotarPropriedadesSemDono(usuarioId: String): Int
}
