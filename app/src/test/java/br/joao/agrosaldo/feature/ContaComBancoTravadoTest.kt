package br.joao.agrosaldo.feature

import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.DespachantesDeTeste
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.relogioEm
import br.joao.agrosaldo.data.relogioFixo
import br.joao.agrosaldo.data.relogioPorMs
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.data.repository.UsuarioRepositoryMemoria
import br.joao.agrosaldo.data.sessao.BloqueioMemoria
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.data.sessao.TentativasMemoria
import br.joao.agrosaldo.domain.RelogioMonotonicoIndisponivel
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.HashDeSenha
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.Usuario
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import br.joao.agrosaldo.feature.conta.AlterarSenhaViewModel
import br.joao.agrosaldo.feature.conta.CadastroViewModel
import br.joao.agrosaldo.feature.conta.ContaViewModel
import br.joao.agrosaldo.feature.conta.LoginViewModel
import br.joao.agrosaldo.feature.conta.NovoConsentimentoViewModel
import br.joao.agrosaldo.feature.sessao.SessaoViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Telas de conta e sessão com o banco travado: viram mensagem e nunca fecham o app (FLW-01, FLW-02). */
@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle
class ContaComBancoTravadoTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val usuarios = UsuarioRepositoryMemoria()
    private val sessao = SessaoMemoria()
    private val conta = contaSobre(usuarios)
    private val propriedades = PropriedadeRepositoryMemoria()
    private val selecao = SelecaoAtual(relogio = relogioEm(LocalDate.of(2026, 9, 30)))
    private val bloqueio = BloqueioMemoria()

    private fun contaSobre(repositorio: UsuarioRepository) = ContaService(
        usuarios = repositorio,
        sessao = sessao,
        tentativas = TentativasMemoria(),
        // Poucas iterações só para o teste ser rápido.
        hash = HashDeSenha(iteracoes = 1_000),
        relogio = relogioPorMs { 1_790_808_000_000L },
        despachantes = DespachantesDeTeste(Dispatchers.Unconfined),
        relogioMonotonico = RelogioMonotonicoIndisponivel
    )

    private fun sessaoViewModel(conta: ContaService) =
        SessaoViewModel(conta, BloqueioMemoria(), propriedades, RelogioMonotonicoIndisponivel, selecao)

    // ---------- banco travado nas telas de conta e na sessão (FLW-01, FLW-02) ----------

    /** Toda leitura e gravação de conta lança enquanto [quebrado]: simula o banco travado. */
    private class UsuariosInstaveis(private val real: UsuarioRepository) : UsuarioRepository by real {
        var quebrado = true

        private fun conferir() = check(!quebrado) { "SQLiteDatabaseLockedException: database is locked" }

        override fun observar(id: String): Flow<Usuario?> = flow {
            conferir()
            emitAll(real.observar(id))
        }

        override suspend fun buscarPorId(id: String): Usuario? {
            conferir()
            return real.buscarPorId(id)
        }

        override suspend fun buscarPorEmail(email: String): Usuario? {
            conferir()
            return real.buscarPorEmail(email)
        }

        override suspend fun criar(usuario: Usuario): Boolean {
            conferir()
            return real.criar(usuario)
        }
    }

    private fun cadastroSobre(servico: ContaService) = CadastroViewModel(servico).apply {
        aoMudarNome("Maria Souza")
        aoMudarEmail("maria@exemplo.com")
        aoMudarSenha("milho2026")
        aoMudarConfirmacao("milho2026")
    }

    @Test
    fun `erro ao ler a conta vira mensagem na tela de conta e nao fecha o app`() = runTest {
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        val instaveis = UsuariosInstaveis(usuarios)
        val vm = ContaViewModel(
            contaSobre(instaveis),
            PropriedadeRepositoryMemoria(),
            bloqueio,
            relogio = relogioFixo(Instant.EPOCH, ZoneOffset.UTC)
        )

        assertEquals(ContaViewModel.MENSAGEM_ERRO, vm.uiState.value.erro)

        instaveis.quebrado = false
        advanceUntilIdle()
        assertNull(vm.uiState.value.erro)
        assertEquals("Maria Souza", vm.uiState.value.nome)
    }

    @Test
    fun `banco travado ao excluir a conta vira mensagem e nao fecha o app`() = runTest {
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        val instaveis = UsuariosInstaveis(usuarios).apply { quebrado = false }
        val vm = ContaViewModel(
            contaSobre(instaveis),
            PropriedadeRepositoryMemoria(),
            bloqueio,
            relogio = relogioFixo(Instant.EPOCH, ZoneOffset.UTC)
        )
        instaveis.quebrado = true

        vm.aoExcluirConta("milho2026")

        assertFalse(vm.uiState.value.excluindo)
        assertEquals("Não foi possível excluir a conta. Tente de novo.", vm.uiState.value.erroExclusao)
    }

    @Test
    fun `banco travado no login vira mensagem e libera o botao`() = runTest {
        val vm = LoginViewModel(contaSobre(UsuariosInstaveis(usuarios)))
        vm.aoMudarEmail("maria@exemplo.com")
        vm.aoMudarSenha("milho2026")

        vm.aoEntrar()

        assertFalse(vm.uiState.value.entrando)
        assertEquals("Não foi possível entrar. Tente de novo.", vm.uiState.value.erro)
    }

    @Test
    fun `banco travado ao conferir o e-mail do cadastro vira mensagem`() = runTest {
        val vm = cadastroSobre(contaSobre(UsuariosInstaveis(usuarios)))

        vm.aoContinuar()

        assertFalse(vm.uiState.value.verificando)
        assertFalse(vm.uiState.value.irParaConsentimento)
        assertEquals("Não foi possível conferir o e-mail. Tente de novo.", vm.uiState.value.erroEmail)
    }

    @Test
    fun `banco travado ao criar a conta vira mensagem no consentimento`() = runTest {
        val vm = cadastroSobre(contaSobre(UsuariosInstaveis(usuarios)))

        vm.aoAceitarTermo()

        assertFalse(vm.uiState.value.criando)
        assertEquals("Não foi possível criar a conta. Tente de novo.", vm.uiState.value.erroGeral)
    }

    @Test
    fun `banco travado ao registrar o novo aceite vira mensagem`() = runTest {
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = NovoConsentimentoViewModel(contaSobre(UsuariosInstaveis(usuarios)))

        vm.aoAceitar()

        assertFalse(vm.uiState.value.processando)
        assertEquals("Não foi possível registrar o aceite. Tente de novo.", vm.uiState.value.erro)
    }

    @Test
    fun `banco travado na troca de senha nao fecha o app`() = runTest {
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        // O init lê a conta com o banco travado.
        val vm = AlterarSenhaViewModel(contaSobre(UsuariosInstaveis(usuarios)))
        assertEquals("", vm.uiState.value.email)

        vm.aoMudarSenhaAtual("milho2026")
        vm.aoMudarNovaSenha("cafe2027x")
        vm.aoMudarConfirmacao("cafe2027x")
        vm.aoSalvar()

        assertFalse(vm.uiState.value.salvando)
        assertEquals("Não foi possível trocar a senha. Tente de novo.", vm.uiState.value.erroSenhaAtual)
    }

    @Test
    fun `banco travado ao abrir o app nao fecha o app e a navegacao espera a conta`() = runTest {
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        val instaveis = UsuariosInstaveis(usuarios)
        val vm = sessaoViewModel(contaSobre(instaveis))
        assertNull("a tela fica coberta enquanto a conta não é lida", vm.precisaNovoAceite.value)

        instaveis.quebrado = false
        advanceUntilIdle()
        assertEquals(false, vm.precisaNovoAceite.value)
    }

    @Test
    fun `banco travado ao abrir o clima pela notificacao ignora o pedido`() = runTest {
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        val quebradas = object : PropriedadeRepository by propriedades {
            override suspend fun buscarPorId(id: String): Propriedade? =
                throw IllegalStateException("SQLiteDatabaseLockedException: database is locked")
        }
        val vm = SessaoViewModel(conta, BloqueioMemoria(), quebradas, RelogioMonotonicoIndisponivel, selecao)

        assertFalse(vm.prepararAberturaDoClima(DadosDeExemplo.ID_FAZENDA))
    }
}
