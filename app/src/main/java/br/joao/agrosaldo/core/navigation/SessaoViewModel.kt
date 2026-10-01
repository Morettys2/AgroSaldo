package br.joao.agrosaldo.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.repository.BloqueioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Diz à navegação o estado da sessão. É a única fonte de verdade de:
 *
 * - [logado]: há alguém conectado?
 * - [precisaNovoAceite]: a política mudou desde o último aceite?
 * - [precisaDesbloquear]: o bloqueio com digital está ligado e o app ainda
 *   não foi desbloqueado desde que abriu (ou desde que ficou 2 min fora)?
 *
 * Login, cadastro, sair, aceitar e desbloquear só mexem no estado; a
 * navegação observa estes três e troca de tela sozinha.
 */
class SessaoViewModel(
    private val conta: ContaService,
    private val bloqueio: BloqueioRepository,
    /** Em milissegundos. Precisa ser um relógio que não volta para trás (veja a Factory). */
    private val relogio: () -> Long = System::currentTimeMillis
) : ViewModel() {

    /**
     * stateIn transforma o Flow em StateFlow, que sempre tem um valor atual.
     * O valor inicial vem direto da sessão gravada, então a primeira tela já
     * nasce certa — o app não mostra o login por um instante antes do início.
     */
    val logado: StateFlow<Boolean> = conta.usuarioLogadoId
        .map { id -> id != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, conta.estaLogado())

    val precisaNovoAceite: StateFlow<Boolean> = conta.precisaNovoAceite()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Começa travado: abrir o app com sessão gravada pede a digital.
    private val _desbloqueado = MutableStateFlow(false)
    private var foiParaSegundoPlanoEm: Long? = null

    val precisaDesbloquear: StateFlow<Boolean> =
        combine(logado, bloqueio.ativo, _desbloqueado) { estaLogado, ativo, desbloqueado ->
            estaLogado && ativo && !desbloqueado
        }.stateIn(viewModelScope, SharingStarted.Eagerly, conta.estaLogado() && bloqueio.ativo.value)

    init {
        viewModelScope.launch { conta.validarSessao() }

        // drop(1) pula o valor que já existia ao abrir o app. Daqui pra frente:
        // entrou com e-mail e senha → já está desbloqueado (acabou de provar
        // quem é); saiu → trava para o próximo.
        viewModelScope.launch {
            conta.usuarioLogadoId.drop(1).collect { id -> _desbloqueado.value = id != null }
        }

        // Acabou de ligar o bloqueio na tela de Conta (e provou a digital
        // para isso): não faz sentido travar na cara da pessoa.
        viewModelScope.launch {
            bloqueio.ativo.drop(1).collect { ativo -> if (ativo) _desbloqueado.value = true }
        }
    }

    fun aoDesbloquear() {
        _desbloqueado.value = true
        // Digitar o PIN abre outra tela do sistema, e o app "vai para o
        // fundo" enquanto isso. Esquece essa saída: não foi a pessoa saindo.
        foiParaSegundoPlanoEm = null
    }

    /** "Entrar com e-mail e senha" na tela de bloqueio: sai e cai no login. */
    fun aoUsarSenha() = conta.sair()

    fun aoIrParaSegundoPlano() {
        foiParaSegundoPlanoEm = relogio()
    }

    /**
     * Voltou para o app. Se ficou fora por 2 minutos ou mais, trava de novo —
     * como os apps de banco. Menos que isso (atender uma ligação, olhar o
     * WhatsApp) não incomoda o produtor.
     */
    fun aoVoltarParaPrimeiroPlano() {
        val saiu = foiParaSegundoPlanoEm ?: return
        foiParaSegundoPlanoEm = null
        if (relogio() - saiu >= TEMPO_PARA_TRAVAR_MS) _desbloqueado.value = false
    }

    companion object {
        const val TEMPO_PARA_TRAVAR_MS = 2 * 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // Relógio desde que o celular ligou: mudar a hora do celular
                // não engana o bloqueio, e ele conta o tempo de tela apagada.
                SessaoViewModel(Repositorios.conta, Repositorios.bloqueio, Repositorios.relogioMonotonico)
            }
        }
    }
}
