package br.joao.agrosaldo.domain.clima

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Condição do tempo derivada do código WMO (weather code) da Open-Meteo.
 * Códigos não mapeados viram [DESCONHECIDA].
 */
enum class CondicaoTempo(val rotulo: String, val molhado: Boolean) {
    CEU_LIMPO("Céu limpo", false),
    POUCAS_NUVENS("Poucas nuvens", false),
    NUBLADO("Nublado", false),
    NEBLINA("Neblina", false),
    GAROA("Garoa", true),
    CHUVA("Chuva", true),
    PANCADAS("Pancadas de chuva", true),
    TEMPESTADE("Tempestade", true),
    NEVE("Neve", true),
    DESCONHECIDA("Sem informação", false);

    companion object {
        /** Converte o código WMO da Open-Meteo na condição do tempo. */
        @Suppress("MagicNumber") // tabela de códigos de tempo da OMM (WMO), como a Open-Meteo os devolve
        fun doCodigoWmo(codigo: Int?): CondicaoTempo = when (codigo) {
            0 -> CEU_LIMPO
            1, 2 -> POUCAS_NUVENS
            3 -> NUBLADO
            45, 48 -> NEBLINA
            51, 53, 55, 56, 57 -> GAROA
            61, 63, 65, 66, 67 -> CHUVA
            80, 81, 82 -> PANCADAS
            95, 96, 99 -> TEMPESTADE
            71, 73, 75, 77, 85, 86 -> NEVE
            else -> DESCONHECIDA
        }
    }
}

/** Previsão de um dia da série diária. */
data class PrevisaoDia(
    val data: LocalDate,
    val condicao: CondicaoTempo,
    /** Nulo quando a API não envia o valor (ocorre no último dia da série). */
    val temperaturaMaxima: Double?,
    val temperaturaMinima: Double?,
    val chuvaMm: Double,
    /** 0 a 100. */
    val probabilidadeChuva: Int
)

/** Previsão de uma hora da série horária. */
data class PrevisaoHora(
    /** Hora local da propriedade (fuso informado pela Open-Meteo). */
    val horario: LocalDateTime,
    val probabilidadeChuva: Int,
    val chuvaMm: Double
)

/**
 * Previsão de um ponto: condição atual, série horária e série diária. Os horários estão no fuso
 * da propriedade ([fusoSegundos]), não no do aparelho.
 */
data class Previsao(
    val latitude: Double,
    val longitude: Double,
    val obtidaEm: Instant,
    val fusoSegundos: Int,
    val temperaturaAtual: Double?,
    val condicaoAtual: CondicaoTempo,
    val dias: List<PrevisaoDia>,
    val horas: List<PrevisaoHora>
) {
    /** Converte um instante para a hora local da propriedade. */
    fun horaLocal(instante: Instant): LocalDateTime = LocalDateTime.ofInstant(instante, ZoneOffset.ofTotalSeconds(fusoSegundos))
}
