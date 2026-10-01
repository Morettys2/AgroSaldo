package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A última previsão baixada de cada propriedade — o cache que deixa a tela
 * de clima funcionar sem internet.
 *
 * Guarda a resposta da Open-Meteo como veio (JSON), e não espalhada em
 * tabelas: é um cache, não um dado do produtor. Se o leitor melhorar, a
 * resposta guardada é lida de novo do jeito novo, sem migração.
 *
 * Uma linha por propriedade (a chave é o próprio id dela). Excluir a
 * propriedade leva a previsão junto, pelo CASCADE.
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
    /** Coordenadas (já arredondadas) usadas na busca. Se a propriedade mudar de lugar, o cache não vale mais. */
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "resposta_json")
    val respostaJson: String,
    @ColumnInfo(name = "obtida_em")
    val obtidaEm: Long
)
