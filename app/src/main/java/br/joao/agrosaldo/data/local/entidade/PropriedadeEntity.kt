package br.joao.agrosaldo.data.local.entidade

import androidx.room.ColumnInfo
import androidx.room.Entity
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
 */
@Entity(tableName = "propriedades")
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
    val atualizadoEm: Long
)
