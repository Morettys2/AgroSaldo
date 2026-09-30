package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Como uma conta fica guardada no SQLite.
 *
 * O índice ÚNICO no e-mail faz o próprio banco recusar duas contas com o
 * mesmo e-mail — do mesmo jeito que uma UNIQUE constraint no Oracle.
 *
 * Os instantes são guardados em milissegundos (INTEGER), pelo mesmo motivo
 * da data do lançamento: SQLite não tem tipo de data.
 */
@Entity(
    tableName = "usuarios",
    indices = [Index(value = ["email"], unique = true)]
)
data class UsuarioEntity(
    @PrimaryKey
    val id: String,
    val nome: String,
    val email: String,
    /** Nunca a senha. Só o resultado do PBKDF2, com sal e iterações. */
    @ColumnInfo(name = "senha_hash")
    val senhaHash: String,
    @ColumnInfo(name = "consentimento_em")
    val consentimentoEm: Long,
    @ColumnInfo(name = "versao_termo")
    val versaoTermo: Int,
    @ColumnInfo(name = "criado_em")
    val criadoEm: Long
)
