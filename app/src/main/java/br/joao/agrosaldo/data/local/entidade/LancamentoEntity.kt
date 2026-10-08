package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabela `lancamentos`. FK com CASCADE para `propriedades` (RN02).
 * Índices em `propriedade_id` (filtro) e `data` (ordenação).
 */
@Entity(
    tableName = "lancamentos",
    foreignKeys = [
        ForeignKey(
            entity = PropriedadeEntity::class,
            parentColumns = ["id"],
            childColumns = ["propriedade_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("propriedade_id"),
        Index("data")
    ]
)
data class LancamentoEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "propriedade_id")
    val propriedadeId: String,
    /** Nome de [br.joao.agrosaldo.domain.model.TipoLancamento]. */
    val tipo: String,
    /** Nome de [br.joao.agrosaldo.domain.model.CategoriaLancamento]. */
    val categoria: String,
    /** Centavos (INTEGER); nunca REAL, para evitar erro de ponto flutuante. */
    @ColumnInfo(name = "valor_centavos")
    val valorCentavos: Long,
    /** epochDay (dias desde 1970-01-01); permite ordenar e comparar no SQL. */
    val data: Long,
    val descricao: String,
    @ColumnInfo(name = "atualizado_em")
    val atualizadoEm: Long
)
