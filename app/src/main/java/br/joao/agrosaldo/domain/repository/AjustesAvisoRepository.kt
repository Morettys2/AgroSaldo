package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.clima.LimitesChuva
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

data class AjustesAviso(
    val ativo: Boolean = true,
    val limites: LimitesChuva = LimitesChuva()
)

/** Ajustes do aviso de chuva (tela 18) e a hora do último aviso de cada propriedade. */
interface AjustesAvisoRepository {
    val ajustes: StateFlow<AjustesAviso>
    fun salvar(ajustes: AjustesAviso)
    fun ultimoAvisoEm(propriedadeId: String): Instant?
    fun registrarAviso(propriedadeId: String, em: Instant)
}

/**
 * Liga e desliga a verificação em segundo plano.
 *
 * É uma interface para o ViewModel de ajustes não depender do WorkManager
 * (que precisa de Context). No teste, entra uma versão falsa que só anota.
 */
interface AgendaDeVerificacao {
    fun ligar()
    fun desligar()
    /** Roda uma verificação já, fora do horário. Usado na ferramenta de teste. */
    fun verificarAgora()
}
