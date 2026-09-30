package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Como um lançamento fica guardado no SQLite.
 *
 * FOREIGN KEY com CASCADE: o banco garante que não existe lançamento apontando
 * para propriedade inexistente, e que excluir uma propriedade leva junto os
 * lançamentos dela (RN02). É integridade referencial feita pelo banco, não
 * pelo código — do mesmo jeito que se faria num Oracle ou Postgres.
 *
 * Os índices aceleram as duas perguntas que o app mais faz:
 * "lançamentos desta propriedade" e "ordenados por data".
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
    /** Nome da constante do enum: "RECEITA" ou "DESPESA". */
    val tipo: String,
    /** Nome da constante do enum: "ENERGIA", "ADUBO", ... */
    val categoria: String,
    /** Dinheiro em centavos, INTEGER no SQLite. Nunca REAL: REAL arredonda. */
    @ColumnInfo(name = "valor_centavos")
    val valorCentavos: Long,
    /**
     * Data como número de dias desde 01/01/1970 (epochDay).
     *
     * SQLite não tem tipo de data. Guardar como inteiro faz a ordenação e a
     * comparação funcionarem direto no SQL, sem converter texto.
     */
    val data: Long,
    val descricao: String,
    @ColumnInfo(name = "atualizado_em")
    val atualizadoEm: Long
)
