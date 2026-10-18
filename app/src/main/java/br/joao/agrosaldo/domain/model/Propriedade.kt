package br.joao.agrosaldo.domain.model

import java.text.Collator
import java.util.Locale
import java.util.UUID

/**
 * Propriedade rural (sítio) do usuário.
 *
 * O id é um UUID gerado no cliente para evitar colisão com ids de um
 * backend futuro, o que um autoincremento local não garantiria.
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
        require(latitude in -90.0..90.0) { "Latitude inválida." }
        require(longitude in -180.0..180.0) { "Longitude inválida." }
    }

    companion object {
        const val NOME_MAXIMO = 80
        const val CULTURA_MAXIMA = 60

        /** Maior que qualquer fazenda real; só barra digitação errada (zeros a mais). */
        const val AREA_MAXIMA_HECTARES = 1_000_000.0
    }
}

/**
 * Snapshot de uma exclusão em cascata (RN02): a propriedade e seus lançamentos,
 * com os ids originais, para permitir desfazer.
 */
data class PropriedadeExcluida(val propriedade: Propriedade, val lancamentos: List<Lancamento>)

/**
 * Ordem alfabética do português: acento e maiúscula não mandam o nome para o fim da lista
 * ("Água Limpa" antes de "Boa Vista"), ao contrário do `COLLATE NOCASE` do SQLite. Empates
 * são desfeitos pelo id, para a primeira da lista (a propriedade padrão) ser estável.
 */
fun List<Propriedade>.emOrdemAlfabetica(): List<Propriedade> {
    // Uma instância por chamada: Collator não garante uso seguro entre threads.
    val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
    return sortedWith(compareBy<Propriedade, String>(collator) { it.nome }.thenBy { it.id })
}
