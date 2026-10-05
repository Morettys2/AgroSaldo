package br.joao.agrosaldo.domain.clima

import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.ResultadoAtualizacao
import br.joao.agrosaldo.domain.repository.SessaoRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

/** Um aviso pronto para virar notificação. */
data class AvisoDeChuva(
    val propriedadeId: String,
    val nomePropriedade: String,
    val avaliacao: AvaliacaoChuva
)

data class ResultadoVerificacao(
    val avisos: List<AvisoDeChuva>,
    /** true se alguma propriedade ficou sem previsão por falta de internet. */
    val faltouConexao: Boolean
)

/**
 * O que roda em segundo plano, duas vezes por dia (RN04).
 *
 * Fica no domínio, sem Android: o Worker do WorkManager só chama
 * [verificar] e mostra as notificações que voltarem. Assim a decisão
 * inteira — buscar, avaliar, evitar aviso repetido — é testada sem celular.
 */
class VerificadorDeChuva(
    private val sessao: SessaoRepository,
    private val propriedades: PropriedadeRepository,
    private val clima: ClimaRepository,
    private val ajustes: AjustesAvisoRepository,
    private val relogio: () -> Long = System::currentTimeMillis
) {

    suspend fun verificar(): ResultadoVerificacao {
        val ajustesAtuais = ajustes.ajustes.value
        // Ninguém conectado: não há de quem mostrar dados. Uma notificação
        // com o nome da propriedade de outra conta seria vazamento.
        if (!ajustesAtuais.ativo || sessao.usuarioId.value == null) {
            return ResultadoVerificacao(emptyList(), faltouConexao = false)
        }

        val agora = Instant.ofEpochMilli(relogio())
        var faltouConexao = false
        val avisos = mutableListOf<AvisoDeChuva>()

        // Só os sítios que o produtor deixou ligados na tela de ajustes.
        val monitoradas = propriedades.observarTodas().first().filter { ajustesAtuais.avisaSobre(it.id) }

        for (propriedade in monitoradas) {
            val previsao = when (val resultado = clima.atualizar(propriedade)) {
                is ResultadoAtualizacao.Sucesso -> resultado.previsao
                // Sem internet, ainda dá para avisar com a previsão guardada,
                // desde que ela seja recente (de até 12 h atrás).
                ResultadoAtualizacao.SemConexao -> {
                    faltouConexao = true
                    clima.emCache(propriedade)?.takeIf {
                        Duration.between(it.obtidaEm, agora) <= VALIDADE_PARA_AVISO
                    }
                }
                is ResultadoAtualizacao.Falha -> null
            } ?: continue

            val avaliacao = RegraDeChuva.avaliar(previsao, agora, ajustesAtuais.limites)
            if (!avaliacao.vaiChover) continue
            if (!ControleDeAvisos.podeAvisar(ajustes.ultimoAvisoEm(propriedade.id), agora)) continue

            ajustes.registrarAviso(propriedade.id, agora)
            avisos += AvisoDeChuva(propriedade.id, propriedade.nome, avaliacao)
        }

        return ResultadoVerificacao(avisos, faltouConexao)
    }

    private companion object {
        val VALIDADE_PARA_AVISO: Duration = Duration.ofHours(12)
    }
}
