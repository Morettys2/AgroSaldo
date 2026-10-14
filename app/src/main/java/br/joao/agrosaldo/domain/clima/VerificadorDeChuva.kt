package br.joao.agrosaldo.domain.clima

import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.ResultadoAtualizacao
import br.joao.agrosaldo.domain.repository.SessaoRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first

/** Um aviso pronto para virar notificação. */
data class AvisoDeChuva(
    val propriedadeId: String,
    val nomePropriedade: String,
    val avaliacao: AvaliacaoChuva,
    /** Momento da verificação no fuso da propriedade; base para textos relativos ("amanhã às 2h"). */
    val verificadoEm: LocalDateTime? = null
)

data class ResultadoVerificacao(
    /** Avisos notificados (e registrados) nesta verificação. */
    val avisos: List<AvisoDeChuva>,
    /** true se alguma atualização falhou por motivo passageiro (sem rede ou serviço fora do ar). */
    val falhaPassageira: Boolean
)

/**
 * Verificação periódica de chuva (RN04), executada a cada 12 h pelo Worker.
 *
 * Sem dependência de Android: o Worker chama [verificar] informando como exibir a
 * notificação. Atualiza a previsão, aplica [RegraDeChuva] e [ControleDeAvisos],
 * registra cada aviso logo depois de exibido e esquece o registro de propriedades excluídas.
 */
class VerificadorDeChuva(
    private val sessao: SessaoRepository,
    private val propriedades: PropriedadeRepository,
    private val clima: ClimaRepository,
    private val ajustes: AjustesAvisoRepository,
    private val relogio: () -> Long = System::currentTimeMillis
) {

    /**
     * @param notificar exibe o aviso e devolve false se não pôde (permissão negada ou
     *   notificações desligadas). Cada aviso é notificado assim que avaliado e só então
     *   registrado: um aviso que ninguém viu não bloqueia o próximo por [ControleDeAvisos],
     *   nem quando a verificação é interrompida no meio.
     */
    suspend fun verificar(notificar: (AvisoDeChuva) -> Boolean): ResultadoVerificacao {
        val ajustesAtuais = ajustes.ajustes.value
        // Sem sessão ativa não há dono dos dados; notificar exporia propriedades de outra conta.
        val usuarioId = sessao.usuarioId.value
        if (!ajustesAtuais.ativo || usuarioId == null) {
            return ResultadoVerificacao(emptyList(), falhaPassageira = false)
        }
        // Logout ou troca de conta durante a verificação: o resto pertence à conta anterior.
        fun mesmaConta() = sessao.usuarioId.value == usuarioId

        val agora = Instant.ofEpochMilli(relogio())
        var falhaPassageira = false
        val avisos = mutableListOf<AvisoDeChuva>()

        val todas = propriedades.observarTodas().first()
        if (!mesmaConta()) return ResultadoVerificacao(emptyList(), falhaPassageira = false)
        // O registro do último aviso de propriedades excluídas não serve mais.
        ajustes.esquecerAvisosExceto(todas.map { it.id }.toSet())

        for (propriedade in todas.filter { ajustesAtuais.avisaSobre(it.id) }) {
            val previsao = when (val resultado = clima.atualizar(propriedade)) {
                is ResultadoAtualizacao.Sucesso -> resultado.previsao
                // Sem rede ou serviço fora do ar: usa o cache se tiver no máximo
                // VALIDADE_PARA_AVISO e pede nova tentativa.
                ResultadoAtualizacao.SemConexao, is ResultadoAtualizacao.Indisponivel -> {
                    falhaPassageira = true
                    clima.emCache(propriedade)?.takeIf {
                        Duration.between(it.obtidaEm, agora) <= VALIDADE_PARA_AVISO
                    }
                }
                // Resposta inválida ou propriedade excluída: tentar de novo não resolve.
                is ResultadoAtualizacao.Falha -> null
            } ?: continue
            // O download pode ter demorado: confere a conta antes de expor o nome da propriedade.
            if (!mesmaConta()) break

            val avaliacao = RegraDeChuva.avaliar(previsao, agora, ajustesAtuais.limites)
            if (!avaliacao.vaiChover) continue
            if (!ControleDeAvisos.podeAvisar(ajustes.ultimoAvisoEm(propriedade.id), agora)) continue

            val aviso = AvisoDeChuva(propriedade.id, propriedade.nome, avaliacao, previsao.horaLocal(agora))
            if (!notificar(aviso)) continue
            ajustes.registrarAviso(propriedade.id, agora)
            avisos += aviso
        }

        // Nova tentativa só faz sentido para a conta que começou a verificação.
        return ResultadoVerificacao(avisos, falhaPassageira && mesmaConta())
    }

    private companion object {
        val VALIDADE_PARA_AVISO: Duration = Duration.ofHours(12)
    }
}
