package br.joao.agrosaldo.domain.clima

import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.ResultadoAtualizacao
import br.joao.agrosaldo.domain.repository.SessaoRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/** Um aviso pronto para virar notificação. */
data class AvisoDeChuva(
    val propriedadeId: String,
    val nomePropriedade: String,
    val avaliacao: AvaliacaoChuva,
    /** Momento da verificação no fuso da propriedade; base para textos relativos ("amanhã às 2h"). */
    val verificadoEm: LocalDateTime? = null
)

data class ResultadoVerificacao(
    val avisos: List<AvisoDeChuva>,
    /** true se alguma atualização falhou por falta de conexão. */
    val faltouConexao: Boolean
)

/**
 * Verificação periódica de chuva (RN04), executada a cada 12 h pelo Worker.
 *
 * Sem dependência de Android: o Worker chama [verificar] e apenas exibe os
 * avisos retornados. Atualiza a previsão, aplica [RegraDeChuva] e
 * [ControleDeAvisos] e registra cada aviso emitido.
 */
class VerificadorDeChuva(
    private val sessao: SessaoRepository,
    private val propriedades: PropriedadeRepository,
    private val clima: ClimaRepository,
    private val ajustes: AjustesAvisoRepository,
    private val relogio: () -> Long = System::currentTimeMillis
) {

    /**
     * @param podeNotificar false se a permissão de notificação foi negada ou as
     *   notificações estão desativadas. A previsão é atualizada, mas nenhum aviso
     *   é registrado, para não bloquear o próximo aviso real por [ControleDeAvisos].
     */
    suspend fun verificar(podeNotificar: Boolean = true): ResultadoVerificacao {
        val ajustesAtuais = ajustes.ajustes.value
        // Sem sessão ativa não há dono dos dados; notificar exporia propriedades de outra conta.
        if (!ajustesAtuais.ativo || sessao.usuarioId.value == null) {
            return ResultadoVerificacao(emptyList(), faltouConexao = false)
        }

        val agora = Instant.ofEpochMilli(relogio())
        var faltouConexao = false
        val avisos = mutableListOf<AvisoDeChuva>()

        val monitoradas = propriedades.observarTodas().first().filter { ajustesAtuais.avisaSobre(it.id) }

        for (propriedade in monitoradas) {
            val previsao = when (val resultado = clima.atualizar(propriedade)) {
                is ResultadoAtualizacao.Sucesso -> resultado.previsao
                // Offline: usa o cache se tiver no máximo VALIDADE_PARA_AVISO.
                ResultadoAtualizacao.SemConexao -> {
                    faltouConexao = true
                    clima.emCache(propriedade)?.takeIf {
                        Duration.between(it.obtidaEm, agora) <= VALIDADE_PARA_AVISO
                    }
                }
                is ResultadoAtualizacao.Falha -> null
            } ?: continue

            val avaliacao = RegraDeChuva.avaliar(previsao, agora, ajustesAtuais.limites)
            if (!avaliacao.vaiChover || !podeNotificar) continue
            if (!ControleDeAvisos.podeAvisar(ajustes.ultimoAvisoEm(propriedade.id), agora)) continue

            ajustes.registrarAviso(propriedade.id, agora)
            avisos += AvisoDeChuva(propriedade.id, propriedade.nome, avaliacao, previsao.horaLocal(agora))
        }

        return ResultadoVerificacao(avisos, faltouConexao)
    }

    private companion object {
        val VALIDADE_PARA_AVISO: Duration = Duration.ofHours(12)
    }
}
