package br.joao.agrosaldo.domain.model

import java.text.Collator
import java.util.Locale
import java.util.UUID

/**
 * Modelo de domínio da propriedade rural (sítio). O id é um UUID gerado no app, sem depender
 * de autoincremento do banco. O `init` valida nome, cultura, área e coordenadas.
 */
data class Propriedade(
    val id: String = UUID.randomUUID().toString(),
    val nome: String,
    val cultura: String,
    val areaHectares: Double,
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(nome.isNotBlank()) { "O nome da propriedade não pode ser vazio." }
        require(nome.length <= NOME_MAXIMO) { "O nome pode ter no máximo $NOME_MAXIMO caracteres." }
        require(cultura.length <= CULTURA_MAXIMA) { "A cultura pode ter no máximo $CULTURA_MAXIMA caracteres." }
        // isFinite: NaN e infinito passariam por "> 0.0" e estragariam as somas e a exibição.
        require(areaHectares.isFinite() && areaHectares > 0.0) { "A área deve ser maior que zero." }
        require(areaHectares <= AREA_MAXIMA_HECTARES) { "A área máxima é 1.000.000 ha." }
        require(latitude in LATITUDES) { "Latitude inválida." }
        require(longitude in LONGITUDES) { "Longitude inválida." }
    }

    companion object {
        /** Limites validados no `init`. */
        const val NOME_MAXIMO = 80

        val LATITUDES: ClosedFloatingPointRange<Double> = -90.0..90.0
        val LONGITUDES: ClosedFloatingPointRange<Double> = -180.0..180.0
        const val CULTURA_MAXIMA = 60

        /** Teto da área em hectares: barra valor digitado com zeros a mais. */
        const val AREA_MAXIMA_HECTARES = 1_000_000.0
    }
}

/**
 * Resultado de uma exclusão em cascata: a propriedade e os lançamentos removidos, com os ids
 * originais, para o desfazer reinserir exatamente o que saiu.
 */
data class PropriedadeExcluida(val propriedade: Propriedade, val lancamentos: List<Lancamento>)

/**
 * Ordena pela ordem alfabética do português (`Collator` pt-BR): acento e maiúscula não mandam o
 * nome para o fim da lista, ao contrário do `COLLATE NOCASE` do SQLite. Empates são desfeitos
 * pelo id, para a ordem ser estável.
 */
fun List<Propriedade>.emOrdemAlfabetica(): List<Propriedade> {
    // Uma instância por chamada: Collator não garante uso seguro entre threads.
    val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
    return sortedWith(compareBy<Propriedade, String>(collator) { it.nome }.thenBy { it.id })
}
