package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidade da tabela `propriedades` (`@Entity`). A chave estrangeira `usuario_id` → `usuarios.id`
 * tem ON DELETE CASCADE: excluir a conta apaga as propriedades e, em cadeia, os lançamentos
 * e as previsões. O índice em `usuario_id` acelera o filtro por dono.
 */
@Entity(
    tableName = "propriedades",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("usuario_id")]
)
data class PropriedadeEntity(
    @PrimaryKey
    val id: String,
    val nome: String,
    val cultura: String,
    @ColumnInfo(name = "area_hectares")
    val areaHectares: Double,
    val latitude: Double,
    val longitude: Double,
    /** Instante da última gravação, em epoch ms. */
    @ColumnInfo(name = "atualizado_em")
    val atualizadoEm: Long,
    /**
     * Dono (chave estrangeira para `usuarios.id`); nulo indica propriedade sem dono.
     * Fica por último porque a ordem das colunas precisa coincidir com o schema exportado.
     */
    @ColumnInfo(name = "usuario_id")
    val usuarioId: String?
)
