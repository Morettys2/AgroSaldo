package br.joao.agrosaldo.core.util

import br.joao.agrosaldo.domain.clima.AvaliacaoChuva
import br.joao.agrosaldo.domain.clima.LimitesChuva
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Textos de previsão de chuva compartilhados pelo Início, pela tela de Clima e pela
 * notificação, para que as três exibam a mesma previsão de forma consistente.
 */
object TextosDaChuva {

    /**
     * Probabilidade (%) a partir da qual não se exibe "sem chuva", mesmo abaixo do
     * limite configurado (abaixo de 40% é a faixa "pouco provável").
     */
    const val CHANCE_DESPREZIVEL = 40

    /** Abaixo disto o volume arredondaria para "0 mm" e é omitido. */
    private const val VOLUME_DESPREZIVEL_MM = 0.05

    /** "6 mm", ou null quando a chuva prevista é desprezível. */
    fun volume(chuvaMm: Double): String? = if (chuvaMm < VOLUME_DESPREZIVEL_MM) null else chuvaMm.emMilimetros()

    /** "85% de chance · 6 mm", ou só "85% de chance" quando o volume é zero. */
    fun chanceEVolume(avaliacao: AvaliacaoChuva, separador: String = " · "): String =
        listOfNotNull("${avaliacao.probabilidadeMaxima}% de chance", volume(avaliacao.chuvaTotalMm))
            .joinToString(separador)

    /** "85% · 6 mm". */
    fun chanceEVolumeCurto(avaliacao: AvaliacaoChuva): String =
        listOfNotNull("${avaliacao.probabilidadeMaxima}%", volume(avaliacao.chuvaTotalMm)).joinToString(" · ")

    /** Limites configurados: "90% ou 2 mm". */
    fun limites(limites: LimitesChuva): String = "${limites.probabilidadeMinima}% ou ${limites.chuvaMinimaMm.emMilimetros()}"

    /** Abaixo do limite de aviso, mas com chance ou volume não desprezível. */
    fun podeChoverAbaixoDoLimite(avaliacao: AvaliacaoChuva): Boolean = !avaliacao.vaiChover &&
        (avaliacao.probabilidadeMaxima >= CHANCE_DESPREZIVEL || volume(avaliacao.chuvaTotalMm) != null)

    /** true se o início é na hora corrente ou antes. */
    fun jaComecou(inicio: LocalDateTime, agora: LocalDateTime): Boolean = !inicio.isAfter(agora.truncatedTo(ChronoUnit.HOURS))

    /**
     * "às 18h", "à 1h", "à meia-noite", "amanhã às 2h", "depois de amanhã às 2h".
     * Inclui o dia quando não é hoje, pois a janela pode chegar a 48 h.
     * Não trata chuva já iniciada; ver [jaComecou].
     */
    fun quando(inicio: LocalDateTime, agora: LocalDateTime): String {
        val hora = horaComArtigo(inicio.hour, "à", "às")
        return when (diasEntre(agora, inicio)) {
            0L -> hora
            1L -> "amanhã $hora"
            else -> "depois de amanhã $hora"
        }
    }

    /** "a partir das 18h", "a partir da 1h de amanhã" ou "já nesta hora". */
    fun aPartirDe(inicio: LocalDateTime, agora: LocalDateTime): String {
        if (jaComecou(inicio, agora)) return "já nesta hora"
        return "a partir ${horaComArtigo(inicio.hour, "da", "das")}${deQueDia(agora, inicio)}"
    }

    /** "a partir das 18h", sem o dia; para quando o horário de referência é desconhecido. */
    fun aPartirDaHora(inicio: LocalDateTime): String = "a partir ${horaComArtigo(inicio.hour, "da", "das")}"

    /** Fim da janela de previsão: "até as 2h de amanhã", "até a meia-noite". */
    fun ate(fim: LocalDateTime, agora: LocalDateTime): String = "até ${horaComArtigo(fim.hour, "a", "as")}${deQueDia(agora, fim)}"

    /** Artigo concorda com a hora: [singular] para 1h e meia-noite ("à 1h"), [plural] nas demais ("às 2h"). */
    private fun horaComArtigo(hora: Int, singular: String, plural: String): String = when (hora) {
        0 -> "$singular meia-noite"
        1 -> "$singular 1h"
        else -> "$plural ${hora}h"
    }

    private fun diasEntre(agora: LocalDateTime, momento: LocalDateTime): Long =
        ChronoUnit.DAYS.between(agora.toLocalDate(), momento.toLocalDate()).coerceAtLeast(0L)

    private fun deQueDia(agora: LocalDateTime, momento: LocalDateTime): String = when (diasEntre(agora, momento)) {
        0L -> ""
        1L -> " de amanhã"
        else -> " de depois de amanhã"
    }
}
