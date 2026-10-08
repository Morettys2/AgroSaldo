package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.clima.LimitesChuva
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

data class AjustesAviso(
    val ativo: Boolean = true,
    val limites: LimitesChuva = LimitesChuva(),
    /**
     * Ids das propriedades com aviso desativado. Lista de exclusão (e não de
     * inclusão) para que propriedades novas já nasçam monitoradas.
     */
    val propriedadesSemAviso: Set<String> = emptySet()
) {
    fun avisaSobre(propriedadeId: String): Boolean = propriedadeId !in propriedadesSemAviso
}

/**
 * Ajustes do aviso de chuva e instante do último aviso emitido por propriedade, da conta
 * conectada: [ajustes] acompanha as trocas de sessão e, sem sessão, traz os padrões.
 */
interface AjustesAvisoRepository {
    val ajustes: StateFlow<AjustesAviso>
    fun salvar(ajustes: AjustesAviso)
    fun ultimoAvisoEm(propriedadeId: String): Instant?
    fun registrarAviso(propriedadeId: String, em: Instant)

    /** Esquece o último aviso das propriedades da conta que não estão em [existentes]. */
    fun esquecerAvisosExceto(existentes: Set<String>)

    /** Apaga os ajustes e os avisos registrados de [usuarioId] (exclusão da conta). */
    fun excluirDadosDoUsuario(usuarioId: String)
}

/** Agendamento da verificação periódica; abstrai o WorkManager (que exige Context). */
interface AgendaDeVerificacao {
    fun ligar()
    fun desligar()
    /** Enfileira uma verificação imediata, fora do agendamento periódico. */
    fun verificarAgora()
}
