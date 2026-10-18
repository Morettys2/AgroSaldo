package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabela `propriedades`. FK `usuario_id` com CASCADE (desde a versão 2):
 * excluir a conta remove as propriedades e, em cadeia, lançamentos e previsões.
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
    /** Última gravação (epoch ms). Não exibido; reservado para sincronização futura. */
    @ColumnInfo(name = "atualizado_em")
    val atualizadoEm: Long,
    /**
     * Dono. Nulo apenas para dados de exemplo, até serem adotados pela
     * primeira conta criada.
     *
     * Deve ser a última coluna: a ordem precisa coincidir com o schema exportado.
     */
    @ColumnInfo(name = "usuario_id")
    val usuarioId: String?
)
