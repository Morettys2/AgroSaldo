package br.joao.agrosaldo.data.preferencias

import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAviso
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/** Fakes para os testes. */
class AjustesAvisoMemoria(inicial: AjustesAviso = AjustesAviso()) : AjustesAvisoRepository {
    private val _ajustes = MutableStateFlow(inicial)
    override val ajustes: StateFlow<AjustesAviso> = _ajustes.asStateFlow()
    private val ultimos = mutableMapOf<String, Instant>()

    override fun salvar(ajustes: AjustesAviso) {
        _ajustes.value = ajustes
    }

    override fun ultimoAvisoEm(propriedadeId: String): Instant? = ultimos[propriedadeId]

    override fun registrarAviso(propriedadeId: String, em: Instant) {
        ultimos[propriedadeId] = em
    }
}

class AgendaFalsa : AgendaDeVerificacao {
    var ligada: Boolean? = null
        private set

    override fun ligar() {
        ligada = true
    }

    override fun desligar() {
        ligada = false
    }

    var verificacoesManuais = 0
        private set

    override fun verificarAgora() {
        verificacoesManuais++
    }
}
