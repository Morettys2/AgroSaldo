package br.joao.agrosaldo.domain.clima

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Limites configuráveis do aviso de chuva.
 *
 * Padrão: probabilidade >= 60% OU chuva acumulada >= 2 mm nas próximas 12 h.
 * O construtor rejeita valores fora das faixas do companion.
 */
data class LimitesChuva(val probabilidadeMinima: Int = 60, val chuvaMinimaMm: Double = 2.0, val janelaHoras: Int = 12) {
    init {
        require(probabilidadeMinima in PROBABILIDADE_FAIXA) { "Probabilidade fora da faixa." }
        require(chuvaMinimaMm in CHUVA_FAIXA) { "Volume de chuva fora da faixa." }
        require(janelaHoras in JANELA_FAIXA) { "Janela fora da faixa." }
    }

    companion object {
        val PROBABILIDADE_FAIXA = 10..100
        val CHUVA_FAIXA = 0.5..50.0
        val JANELA_FAIXA = 3..48
    }
}

enum class MotivoChuva { PROBABILIDADE, VOLUME, AMBOS }

data class AvaliacaoChuva(
    val vaiChover: Boolean,
    /** Maior chance de chuva entre as horas da janela. */
    val probabilidadeMaxima: Int,
    /** Chuva somada na janela, em mm. */
    val chuvaTotalMm: Double,
    /**
     * Primeira hora da janela que atinge o limite de chance ou tem pelo menos
     * [RegraDeChuva.CHUVA_MINIMA_POR_HORA_MM]; null se não vai chover.
     */
    val inicioPrevisto: LocalDateTime?,
    val motivo: MotivoChuva?,
    /** false quando a previsão guardada não cobre a janela (velha demais). */
    val temDados: Boolean
)

/**
 * Decide se a previsão justifica um aviso de chuva.
 *
 * Chove se, nas próximas [LimitesChuva.janelaHoras] horas, a maior
 * probabilidade horária for >= o limite OU a chuva somada for >= o limite em mm.
 * Ambas as comparações são inclusivas. Função pura, coberta por testes.
 */
object RegraDeChuva {

    fun avaliar(previsao: Previsao, agora: Instant, limites: LimitesChuva = LimitesChuva()): AvaliacaoChuva {
        // A janela inclui a hora corrente inteira (14h40 -> começa às 14h).
        val inicio = previsao.horaLocal(agora).truncatedTo(ChronoUnit.HOURS)
        val fim = inicio.plusHours(limites.janelaHoras.toLong())

        val janela = previsao.horas.filter { !it.horario.isBefore(inicio) && it.horario.isBefore(fim) }
        if (janela.isEmpty()) {
            return AvaliacaoChuva(false, 0, 0.0, null, null, temDados = false)
        }

        val probabilidadeMaxima = janela.maxOf { it.probabilidadeChuva }
        val chuvaTotal = janela.sumOf { it.chuvaMm }

        val porProbabilidade = probabilidadeMaxima >= limites.probabilidadeMinima
        val porVolume = chuvaTotal >= limites.chuvaMinimaMm - TOLERANCIA
        val motivo = when {
            porProbabilidade && porVolume -> MotivoChuva.AMBOS
            porProbabilidade -> MotivoChuva.PROBABILIDADE
            porVolume -> MotivoChuva.VOLUME
            else -> null
        }

        val inicioPrevisto = janela.firstOrNull {
            it.probabilidadeChuva >= limites.probabilidadeMinima || it.chuvaMm >= CHUVA_MINIMA_POR_HORA_MM - TOLERANCIA
        }?.horario

        return AvaliacaoChuva(
            vaiChover = motivo != null,
            probabilidadeMaxima = probabilidadeMaxima,
            chuvaTotalMm = chuvaTotal,
            inicioPrevisto = if (motivo != null) inicioPrevisto else null,
            motivo = motivo,
            temDados = true
        )
    }

    /** Volume mínimo de uma hora para marcar o início da chuva; abaixo disso é traço de garoa. */
    const val CHUVA_MINIMA_POR_HORA_MM = 0.1

    // Absorve erro de arredondamento da soma de Double (ex.: 1,9999999 mm vs. 2 mm).
    private const val TOLERANCIA = 1e-9
}

/**
 * Antirrepetição de avisos por propriedade.
 *
 * A verificação periódica roda a cada 12 h; o intervalo mínimo é 11 h
 * para tolerar a imprecisão de agendamento do WorkManager.
 */
object ControleDeAvisos {
    val INTERVALO_MINIMO: Duration = Duration.ofHours(11)

    fun podeAvisar(ultimoAvisoEm: Instant?, agora: Instant): Boolean =
        ultimoAvisoEm == null || Duration.between(ultimoAvisoEm, agora) >= INTERVALO_MINIMO
}

/**
 * Política de cache da previsão.
 *
 * A Open-Meteo atualiza os modelos de hora em hora; buscar com mais frequência
 * só consome dados móveis e a cota gratuita da API.
 */
object PoliticaDeAtualizacao {
    val VALIDADE: Duration = Duration.ofHours(1)

    /** Idade máxima para exibir a previsão em cache como "última conhecida". */
    val VALIDADE_MAXIMA_CACHE: Duration = Duration.ofDays(2)

    fun precisaAtualizar(obtidaEm: Instant?, agora: Instant): Boolean = obtidaEm == null || Duration.between(obtidaEm, agora) >= VALIDADE
}
