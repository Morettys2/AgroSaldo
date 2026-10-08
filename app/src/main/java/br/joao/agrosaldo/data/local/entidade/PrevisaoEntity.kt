package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Tabela `previsoes`: última resposta da Open-Meteo por propriedade (PK =
 * id da propriedade, FK com CASCADE).
 *
 * Guarda o JSON bruto para que mudanças no parser não exijam migração.
 */
@Entity(
    tableName = "previsoes",
    foreignKeys = [
        ForeignKey(
            entity = PropriedadeEntity::class,
            parentColumns = ["id"],
            childColumns = ["propriedade_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PrevisaoEntity(
    @PrimaryKey
    @ColumnInfo(name = "propriedade_id")
    val propriedadeId: String,
    /** Coordenadas arredondadas usadas na requisição; divergência invalida o cache. */
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "resposta_json")
    val respostaJson: String,
    @ColumnInfo(name = "obtida_em")
    val obtidaEm: Long
)
