package br.joao.agrosaldo.domain.clima

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Os limites que o produtor escolhe na tela de ajustes (RN04).
 *
 * Os valores padrão são os da proposta: 60% de chance OU 2 mm de chuva
 * somada, olhando as próximas 12 horas.
 */
data class LimitesChuva(
    val probabilidadeMinima: Int = 60,
    val chuvaMinimaMm: Double = 2.0,
    val janelaHoras: Int = 12
) {
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
    /** Primeira hora da janela em que a chuva aparece, se aparecer. */
    val inicioPrevisto: LocalDateTime?,
    val motivo: MotivoChuva?,
    /** false quando a previsão guardada não cobre a janela (velha demais). */
    val temDados: Boolean
)

/**
 * RN04 — a regra que decide se vale avisar o produtor.
 *
 * Chove se, nas próximas [LimitesChuva.janelaHoras] horas:
 *   a maior probabilidade de chuva for ≥ o limite, OU
 *   a chuva somada for ≥ o limite em mm.
 *
 * Os dois testes são "maior ou igual": exatamente 60% já conta. É uma
 * função pura — mesma entrada, mesma saída — e por isso é toda coberta por
 * teste, que é o que o CLAUDE.md pede para esta regra.
 */
object RegraDeChuva {

    fun avaliar(previsao: Previsao, agora: Instant, limites: LimitesChuva = LimitesChuva()): AvaliacaoChuva {
        // A hora atual inteira conta: às 14h40, a janela começa nas 14h.
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
            it.probabilidadeChuva >= limites.probabilidadeMinima || it.chuvaMm > 0.0
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

    // Soma de Double: 0.1 + 0.2 dá 0.30000000000000004. A tolerância evita
    // que 1,9999999 mm deixe de contar como 2 mm.
    private const val TOLERANCIA = 1e-9
}

/**
 * Evita mandar o mesmo aviso várias vezes.
 *
 * A verificação em segundo plano roda a cada 12 h (2× por dia, RN04). O
 * intervalo mínimo entre avisos da mesma propriedade é um pouco menor que
 * isso, porque o Android pode atrasar ou adiantar a execução alguns minutos.
 */
object ControleDeAvisos {
    val INTERVALO_MINIMO: Duration = Duration.ofHours(11)

    fun podeAvisar(ultimoAvisoEm: Instant?, agora: Instant): Boolean =
        ultimoAvisoEm == null || Duration.between(ultimoAvisoEm, agora) >= INTERVALO_MINIMO
}

/**
 * Quando vale buscar previsão nova na internet.
 *
 * A Open-Meteo atualiza os modelos de hora em hora; buscar mais que isso só
 * gasta a internet do produtor (que no sítio costuma ser fraca) e a cota
 * gratuita do serviço.
 */
object PoliticaDeAtualizacao {
    val VALIDADE: Duration = Duration.ofHours(1)

    /** Previsão mais velha que isso não serve nem para mostrar como "última conhecida". */
    val VALIDADE_MAXIMA_CACHE: Duration = Duration.ofDays(2)

    fun precisaAtualizar(obtidaEm: Instant?, agora: Instant): Boolean =
        obtidaEm == null || Duration.between(obtidaEm, agora) >= VALIDADE
}
