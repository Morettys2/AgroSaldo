package br.joao.agrosaldo.core.util

import br.joao.agrosaldo.domain.clima.AvaliacaoChuva
import br.joao.agrosaldo.domain.clima.LimitesChuva
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * As frases da chuva, num lugar só.
 *
 * O cartão do Início, a tela de Clima e a notificação falam da mesma
 * previsão. Antes, cada um montava o próprio texto e eles se contradiziam:
 * um mostrava "0 mm", outro escondia; um dizia "Sem chuva" do lado de "82%
 * de chance". Aqui as três telas usam as mesmas regras de escrita — e elas
 * são testadas sem abrir o app.
 */
object TextosDaChuva {

    /**
     * Abaixo disto (e sem volume nenhum) dá para dizer "sem chuva": até 39%
     * a previsão do tempo chama de "pouco provável". A partir daqui, e
     * abaixo do limite do produtor, o app diz "pode chover".
     */
    const val CHANCE_DESPREZIVEL = 40

    /** Menos que isso aparece como "0 mm" — melhor nem mostrar. */
    private const val VOLUME_DESPREZIVEL_MM = 0.05

    /** "6 mm", ou null quando a chuva prevista é desprezível. */
    fun volume(chuvaMm: Double): String? =
        if (chuvaMm < VOLUME_DESPREZIVEL_MM) null else chuvaMm.emMilimetros()

    /** "85% de chance · 6 mm", ou só "85% de chance" quando o volume é zero. */
    fun chanceEVolume(avaliacao: AvaliacaoChuva, separador: String = " · "): String =
        listOfNotNull("${avaliacao.probabilidadeMaxima}% de chance", volume(avaliacao.chuvaTotalMm))
            .joinToString(separador)

    /** "85% · 6 mm" — a versão curta, do cabeçalho do Clima. */
    fun chanceEVolumeCurto(avaliacao: AvaliacaoChuva): String =
        listOfNotNull("${avaliacao.probabilidadeMaxima}%", volume(avaliacao.chuvaTotalMm)).joinToString(" · ")

    /** "90% ou 2 mm" — os limites que o produtor escolheu. */
    fun limites(limites: LimitesChuva): String =
        "${limites.probabilidadeMinima}% ou ${limites.chuvaMinimaMm.emMilimetros()}"

    /** Abaixo do limite, mas com chance de verdade: não dá para dizer "sem chuva". */
    fun podeChoverAbaixoDoLimite(avaliacao: AvaliacaoChuva): Boolean =
        !avaliacao.vaiChover &&
            (avaliacao.probabilidadeMaxima >= CHANCE_DESPREZIVEL || volume(avaliacao.chuvaTotalMm) != null)

    /** A chuva já começou (ou começa ainda nesta hora)? */
    fun jaComecou(inicio: LocalDateTime, agora: LocalDateTime): Boolean = !inicio.isAfter(agora.truncatedTo(ChronoUnit.HOURS))

    /**
     * Quando a chuva começa, com o dia quando não é hoje: a janela pode
     * passar da meia-noite (e ir até 48 h), e "às 2h" sozinho confundiria
     * com a madrugada que já passou.
     *
     * "às 18h", "à 1h", "à meia-noite", "amanhã às 2h", "depois de amanhã às 2h".
     * Para a chuva que já começou, veja [jaComecou].
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

    /** "a partir das 18h", sem dizer o dia (quando não se sabe a hora de agora). */
    fun aPartirDaHora(inicio: LocalDateTime): String = "a partir ${horaComArtigo(inicio.hour, "da", "das")}"

    /** "até as 2h de amanhã", "até a meia-noite" — o fim da janela que o app está olhando. */
    fun ate(fim: LocalDateTime, agora: LocalDateTime): String =
        "até ${horaComArtigo(fim.hour, "a", "as")}${deQueDia(agora, fim)}"

    /**
     * Português tem artigo na hora: "às 2h", mas "à 1h" e "à meia-noite".
     * [singular] serve para 1h e meia-noite; [plural], para as outras.
     */
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
