package br.joao.agrosaldo.domain.model

import java.util.UUID

/**
 * MODEL — a camada M do MVVM.
 *
 * É uma classe de domínio pura: não conhece Android, Compose, banco nem rede.
 * Se amanhã o app virar desktop ou web, esta classe vem junto sem mudar nada.
 *
 * O id é UUID gerado no cliente, e não autoincremento de banco, porque a API
 * em NestJS entra depois: autoincremento colidiria entre o banco do celular e
 * o do servidor.
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
 * O que sumiu ao excluir uma propriedade: ela e os lançamentos dela (RN02).
 *
 * Guardar tudo junto é o que permite o "Desfazer" trazer de volta
 * exatamente o que existia, com os mesmos ids.
 */
data class PropriedadeExcluida(
    val propriedade: Propriedade,
    val lancamentos: List<Lancamento>
)
