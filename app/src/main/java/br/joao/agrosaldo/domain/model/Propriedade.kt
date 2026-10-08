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
        require(areaHectares > 0.0) { "A área deve ser maior que zero." }
        require(latitude in -90.0..90.0) { "Latitude inválida." }
        require(longitude in -180.0..180.0) { "Longitude inválida." }
    }
}

/**
 * Snapshot de uma exclusão em cascata (RN02): a propriedade e seus lançamentos,
 * com os ids originais, para permitir desfazer.
 */
data class PropriedadeExcluida(
    val propriedade: Propriedade,
    val lancamentos: List<Lancamento>
)

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
