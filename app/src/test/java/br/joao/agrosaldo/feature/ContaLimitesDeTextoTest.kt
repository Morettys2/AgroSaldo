package br.joao.agrosaldo.feature

import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.data.DespachantesDeTeste
import br.joao.agrosaldo.data.relogioPorMs
import br.joao.agrosaldo.data.repository.UsuarioRepositoryMemoria
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.data.sessao.TentativasMemoria
import br.joao.agrosaldo.domain.RelogioMonotonicoIndisponivel
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.HashDeSenha
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import br.joao.agrosaldo.feature.conta.AlterarSenhaViewModel
import br.joao.agrosaldo.feature.conta.CadastroViewModel
import br.joao.agrosaldo.feature.conta.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Campos das telas de conta cortados no limite do domínio ao digitar ou colar (ENT-01). */
@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle
class ContaLimitesDeTextoTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val usuarios = UsuarioRepositoryMemoria()
    private val sessao = SessaoMemoria()
    private val conta = contaSobre(usuarios)

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

    private suspend fun alterarSenhaViewModel(): AlterarSenhaViewModel {
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        return AlterarSenhaViewModel(conta)
    }

    // ---------- limites de texto cortados ao digitar ou colar (ENT-01) ----------

    @Test
    fun `cadastro corta nome, e-mail e senha no limite ao digitar ou colar`() {
        val vm = CadastroViewModel(conta)

        vm.aoMudarNome("N".repeat(300))
        vm.aoMudarEmail("a".repeat(300) + "@exemplo.com")
        vm.aoMudarSenha("a1".repeat(200))
        vm.aoMudarConfirmacao("a1".repeat(200))

        val estado = vm.uiState.value
        assertEquals(RegrasDeConta.NOME_MAXIMO, estado.nome.length)
        assertEquals(RegrasDeConta.EMAIL_MAXIMO, estado.email.length)
        assertEquals(RegrasDeConta.SENHA_MAXIMO, estado.senha.length)
        assertEquals(RegrasDeConta.SENHA_MAXIMO, estado.confirmacao.length)
    }

    @Test
    fun `login corta e-mail e senha no limite ao colar`() {
        val vm = LoginViewModel(conta)

        vm.aoMudarEmail("a".repeat(300) + "@exemplo.com")
        vm.aoMudarSenha("a1".repeat(200))

        assertEquals(RegrasDeConta.EMAIL_MAXIMO, vm.uiState.value.email.length)
        assertEquals(RegrasDeConta.SENHA_MAXIMO, vm.uiState.value.senha.length)
    }

    @Test
    fun `troca de senha corta as senhas no limite ao colar`() = runTest {
        val vm = alterarSenhaViewModel()

        vm.aoMudarSenhaAtual("a1".repeat(200))
        vm.aoMudarNovaSenha("a1".repeat(200))
        vm.aoMudarConfirmacao("a1".repeat(200))

        assertEquals(RegrasDeConta.SENHA_MAXIMO, vm.uiState.value.senhaAtual.length)
        assertEquals(RegrasDeConta.SENHA_MAXIMO, vm.uiState.value.novaSenha.length)
        assertEquals(RegrasDeConta.SENHA_MAXIMO, vm.uiState.value.confirmacao.length)
    }
}
