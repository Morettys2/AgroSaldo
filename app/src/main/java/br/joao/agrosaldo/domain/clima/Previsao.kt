package br.joao.agrosaldo.domain.clima

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Condição do tempo a partir do código WMO que a Open-Meteo devolve.
 *
 * O código é um padrão da Organização Meteorológica Mundial: 0 é céu limpo,
 * 61 é chuva fraca, 95 é tempestade. A tela não precisa conhecer números —
 * ela recebe esta enum e escolhe o ícone.
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

data class PrevisaoDia(
    val data: LocalDate,
    val condicao: CondicaoTempo,
    /** Nulo quando o serviço não mandou o dado (acontece no último dia às vezes). */
    val temperaturaMaxima: Double?,
    val temperaturaMinima: Double?,
    val chuvaMm: Double,
    /** 0 a 100. */
    val probabilidadeChuva: Int
)

data class PrevisaoHora(
    /** Hora local da propriedade (o fuso vem na resposta da Open-Meteo). */
    val horario: LocalDateTime,
    val probabilidadeChuva: Int,
    val chuvaMm: Double
)

/**
 * Uma previsão completa para um ponto: agora, hora a hora e dia a dia.
 *
 * Os horários estão no fuso da PROPRIEDADE, não do celular. Para o produtor
 * em Itobi tanto faz, mas é o certo: "chove às 18h" tem que ser 18h lá.
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
    /** Converte um instante (ex.: agora) para a hora local da propriedade. */
    fun horaLocal(instante: Instant): LocalDateTime =
        LocalDateTime.ofInstant(instante, ZoneOffset.ofTotalSeconds(fusoSegundos))
}
