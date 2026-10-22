package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidade da tabela `lancamentos` (`@Entity`). A chave estrangeira `propriedade_id` →
 * `propriedades.id` tem ON DELETE CASCADE. Índices em `propriedade_id` (filtro) e `data` (ordenação).
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
    /** Nome da constante de [br.joao.agrosaldo.domain.model.TipoLancamento] (RECEITA ou DESPESA). */
    val tipo: String,
    /** Nome da constante de [br.joao.agrosaldo.domain.model.CategoriaLancamento]. */
    val categoria: String,
    /** Valor em centavos (INTEGER); nunca REAL, para não ter erro de ponto flutuante. */
    @ColumnInfo(name = "valor_centavos")
    val valorCentavos: Long,
    /** Data em epochDay (dias desde 1970-01-01); permite ordenar e comparar no SQL. */
    val data: Long,
    val descricao: String,
    /** Instante da última gravação, em epoch ms. */
    @ColumnInfo(name = "atualizado_em")
    val atualizadoEm: Long
)
