package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entidade da tabela `previsoes` (`@Entity`): cache da última resposta da Open-Meteo, uma linha
 * por propriedade. A chave primária é o id da propriedade, que também é chave estrangeira com
 * ON DELETE CASCADE. Guarda o JSON bruto, então mudar o parser não exige migração.
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
    /** Coordenadas arredondadas usadas na requisição; se mudarem, o cache deixa de valer. */
    val latitude: Double,
    val longitude: Double,
    /** Corpo JSON da resposta da API. */
    @ColumnInfo(name = "resposta_json")
    val respostaJson: String,
    /** Instante em que a previsão foi baixada, em epoch ms. */
    @ColumnInfo(name = "obtida_em")
    val obtidaEm: Long
)
