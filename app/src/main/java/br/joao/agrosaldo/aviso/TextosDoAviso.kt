package br.joao.agrosaldo.aviso

import br.joao.agrosaldo.core.util.TextosDaChuva
import br.joao.agrosaldo.domain.clima.AvisoDeChuva

/** Textos da notificação de chuva, sem dependência do Android para permitir teste unitário. */
object TextosDoAviso {

    fun titulo(aviso: AvisoDeChuva): String = "Chuva prevista: ${aviso.nomePropriedade}"

    fun corpo(aviso: AvisoDeChuva, janelaHoras: Int): String {
        val a = aviso.avaliacao
        val volume = TextosDaChuva.volume(a.chuvaTotalMm)?.let { ", com $it previstos" }.orEmpty()
        val quando = a.inicioPrevisto?.let { inicio ->
            val agora = aviso.verificadoEm
            " " + if (agora != null) TextosDaChuva.aPartirDe(inicio, agora) else TextosDaChuva.aPartirDaHora(inicio)
        }.orEmpty()
        return "${a.probabilidadeMaxima}% de chance de chuva nas próximas $janelaHoras horas$volume$quando. " +
            "Dá para adiar a irrigação do pivô e economizar energia."
    }

    /** Versão para tela de bloqueio: sem nome de propriedade nem números. */
    const val TITULO_PUBLICO = "Chuva prevista"
    const val CORPO_PUBLICO = "Abra o AgroSaldo para ver a previsão."
}
