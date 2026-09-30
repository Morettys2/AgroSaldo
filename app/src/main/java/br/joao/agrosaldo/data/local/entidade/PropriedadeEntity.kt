package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Como uma propriedade fica guardada no SQLite.
 *
 * Existe separada de [br.joao.agrosaldo.domain.model.Propriedade] de propósito:
 * o domínio não pode importar Room. Se um dia o banco mudar, só esta classe e
 * o mapeador mudam — o resto do app nem fica sabendo.
 *
 * Os nomes de coluna são em snake_case porque é a convenção de SQL. O Kotlin
 * continua em camelCase; o @ColumnInfo faz a ponte.
 *
 * Desde a versão 2 do banco, cada propriedade tem dono ([usuarioId]). A
 * chave estrangeira com CASCADE faz a exclusão da conta levar junto as
 * propriedades — e, pela chave dos lançamentos, os lançamentos também.
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
    /**
     * Momento da última gravação, em milissegundos.
     *
     * Não aparece em nenhuma tela hoje. Está aqui pensando na API em NestJS:
     * na hora de sincronizar, é por esse campo que se descobre qual versão de
     * um registro é a mais recente.
     */
    @ColumnInfo(name = "atualizado_em")
    val atualizadoEm: Long,
    /**
     * Dono da propriedade. Pode ser nulo só num caso: propriedades que já
     * existiam antes do login (versão 1 do banco) ou os dados de exemplo.
     * A primeira conta criada no aparelho fica com elas.
     *
     * É a última coluna porque a migração 1→2 a acrescenta no fim da tabela;
     * assim o banco novo e o migrado ficam com as colunas na mesma ordem.
     */
    @ColumnInfo(name = "usuario_id")
    val usuarioId: String?
)
