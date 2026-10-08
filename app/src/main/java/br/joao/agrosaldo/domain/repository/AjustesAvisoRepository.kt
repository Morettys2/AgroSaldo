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

/** Ajustes do aviso de chuva e instante do último aviso emitido por propriedade. */
interface AjustesAvisoRepository {
    val ajustes: StateFlow<AjustesAviso>
    fun salvar(ajustes: AjustesAviso)
    fun ultimoAvisoEm(propriedadeId: String): Instant?
    fun registrarAviso(propriedadeId: String, em: Instant)
}

/** Agendamento da verificação periódica; abstrai o WorkManager (que exige Context). */
interface AgendaDeVerificacao {
    fun ligar()
    fun desligar()
    /** Enfileira uma verificação imediata, fora do agendamento periódico. */
    fun verificarAgora()
}
