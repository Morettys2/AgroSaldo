package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabela `usuarios`. Índice único em `email` (normalizado). Instantes em epoch ms.
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
    /** Hash no formato de [br.joao.agrosaldo.domain.conta.HashDeSenha]; nunca a senha. */
    @ColumnInfo(name = "senha_hash")
    val senhaHash: String,
    @ColumnInfo(name = "consentimento_em")
    val consentimentoEm: Long,
    @ColumnInfo(name = "versao_termo")
    val versaoTermo: Int,
    @ColumnInfo(name = "criado_em")
    val criadoEm: Long
)
