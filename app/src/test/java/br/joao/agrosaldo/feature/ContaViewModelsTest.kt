package br.joao.agrosaldo.feature

import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.core.navigation.SessaoViewModel
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.data.repository.UsuarioRepositoryMemoria
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.data.sessao.TentativasMemoria
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.HashDeSenha
import br.joao.agrosaldo.domain.conta.ResultadoLogin
import br.joao.agrosaldo.feature.conta.AlterarSenhaViewModel
import br.joao.agrosaldo.feature.conta.CadastroViewModel
import br.joao.agrosaldo.feature.conta.ContaViewModel
import br.joao.agrosaldo.feature.conta.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneOffset

/**
 * ViewModels do login, do cadastro com consentimento e da conta.
 *
 * O ContaService usado aqui é o de verdade, com repositórios em memória.
 * Assim o teste passa pelo mesmo caminho que o app percorre.
 */
class ContaViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val usuarios = UsuarioRepositoryMemoria()
    private val sessao = SessaoMemoria()
    private val conta = ContaService(
        usuarios = usuarios,
        sessao = sessao,
        tentativas = TentativasMemoria(),
        hash = HashDeSenha(iteracoes = 1_000),
        // 30/09/2026 às 22:40 em UTC
        relogio = { 1_790_808_000_000L },
        dispatcherCalculo = Dispatchers.Unconfined
    )

    private fun cadastroPreenchido() = CadastroViewModel(conta).apply {
        aoMudarNome("Fábio Moretti")
        aoMudarEmail("fabio@exemplo.com")
        aoMudarSenha("milho2026")
        aoMudarConfirmacao("milho2026")
    }

    private suspend fun contaCriadaESaiu() {
        conta.cadastrar("Fábio Moretti", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        conta.sair()
    }

    // ---------- cadastro ----------

    @Test
    fun `cadastro vazio marca os erros e nao avanca`() {
        val vm = CadastroViewModel(conta)
        vm.aoContinuar()

        val estado = vm.uiState.value
        assertNotNull(estado.erroNome)
        assertNotNull(estado.erroEmail)
        assertNotNull(estado.erroSenha)
        assertFalse(estado.irParaConsentimento)
    }

    @Test
    fun `senhas diferentes marcam so a confirmacao`() {
        val vm = cadastroPreenchido()
        vm.aoMudarConfirmacao("milho2025")
        vm.aoContinuar()

        assertNull(vm.uiState.value.erroSenha)
        assertNotNull(vm.uiState.value.erroConfirmacao)
    }

    @Test
    fun `requisitos acompanham a digitacao`() {
        val vm = CadastroViewModel(conta)
        vm.aoMudarSenha("milho")
        assertFalse(vm.uiState.value.requisitos.temTamanhoMinimo)
        assertFalse(vm.uiState.value.requisitos.temLetraENumero)

        vm.aoMudarSenha("milho2026")
        assertTrue(vm.uiState.value.requisitos.todosCumpridos)
    }

    @Test
    fun `formulario certo leva ao consentimento sem gravar nada`() {
        val vm = cadastroPreenchido()
        vm.aoContinuar()

        assertTrue(vm.uiState.value.irParaConsentimento)
        assertTrue(usuarios.todos.isEmpty())
        assertNull(sessao.usuarioId.value)

        vm.aoAbrirConsentimento()
        assertFalse(vm.uiState.value.irParaConsentimento)
    }

    @Test
    fun `email ja cadastrado aparece no proprio campo`() = runTest {
        contaCriadaESaiu()
        val vm = cadastroPreenchido()
        vm.aoContinuar()

        assertEquals("Já existe uma conta com este e-mail.", vm.uiState.value.erroEmail)
        assertFalse(vm.uiState.value.irParaConsentimento)
    }

    @Test
    fun `aceitar o termo cria a conta conecta e esquece a senha`() {
        val vm = cadastroPreenchido()
        vm.aoContinuar()
        vm.aoAceitarTermo()

        assertEquals(1, usuarios.todos.size)
        assertEquals(usuarios.todos.single().id, sessao.usuarioId.value)
        assertEquals("", vm.uiState.value.senha)
        assertEquals("", vm.uiState.value.confirmacao)
    }

    @Test
    fun `consentimento com formulario perdido pede para preencher de novo`() {
        val vm = CadastroViewModel(conta)
        vm.aoAceitarTermo()

        assertNotNull(vm.uiState.value.erroGeral)
        assertTrue(usuarios.todos.isEmpty())
    }

    // ---------- login ----------

    @Test
    fun `login vazio pede e-mail e senha`() {
        val vm = LoginViewModel(conta)
        vm.aoEntrar()
        assertEquals("Informe o e-mail e a senha.", vm.uiState.value.erro)
    }

    @Test
    fun `login errado mostra mensagem generica e limpa a senha`() = runTest {
        contaCriadaESaiu()
        val vm = LoginViewModel(conta)
        vm.aoMudarEmail("fabio@exemplo.com")
        vm.aoMudarSenha("errada123")
        vm.aoEntrar()

        assertEquals("E-mail ou senha incorretos.", vm.uiState.value.erro)
        assertEquals("", vm.uiState.value.senha)
        assertFalse(vm.uiState.value.entrando)
        assertNull(sessao.usuarioId.value)
    }

    @Test
    fun `login certo conecta`() = runTest {
        contaCriadaESaiu()
        val vm = LoginViewModel(conta)
        vm.aoMudarEmail("  fabio@exemplo.com ")
        vm.aoMudarSenha("milho2026")
        vm.aoEntrar()

        assertNull(vm.uiState.value.erro)
        assertEquals(usuarios.todos.single().id, sessao.usuarioId.value)
    }

    @Test
    fun `digitar de novo apaga o erro do login`() {
        val vm = LoginViewModel(conta)
        vm.aoEntrar()
        vm.aoMudarSenha("m")
        assertNull(vm.uiState.value.erro)
    }

    @Test
    fun `quinta tentativa errada avisa do bloqueio`() = runTest {
        contaCriadaESaiu()
        val vm = LoginViewModel(conta)
        vm.aoMudarEmail("fabio@exemplo.com")
        repeat(ContaService.MAXIMO_TENTATIVAS + 1) {
            vm.aoMudarSenha("errada123")
            vm.aoEntrar()
        }
        assertEquals("Muitas tentativas. Tente de novo em 30 segundos.", vm.uiState.value.erro)
    }

    // ---------- conta ----------

    private fun contaViewModel() = ContaViewModel(conta, PropriedadeRepositoryMemoria(), zona = ZoneOffset.UTC)

    @Test
    fun `tela de conta mostra nome email iniciais e data do aceite`() = runTest {
        conta.cadastrar("Fábio Pereira Moretti", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val estado = contaViewModel().uiState.value

        assertFalse(estado.carregando)
        assertEquals("Fábio Pereira Moretti", estado.nome)
        assertEquals("fabio@exemplo.com", estado.email)
        assertEquals("FM", estado.iniciais)
        assertEquals("30/09/2026 às 22:40", estado.consentimentoEm)
        assertEquals("2 cadastradas", estado.resumoPropriedades)
    }

    @Test
    fun `sair pela tela de conta encerra a sessao`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = contaViewModel()
        vm.aoSair()
        assertNull(sessao.usuarioId.value)
    }

    @Test
    fun `excluir com senha errada mostra erro e mantem a conta`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = contaViewModel()

        vm.aoExcluirConta("")
        assertEquals("Digite sua senha para confirmar.", vm.uiState.value.erroExclusao)

        vm.aoExcluirConta("errada123")
        assertEquals("Senha incorreta.", vm.uiState.value.erroExclusao)
        assertEquals(1, usuarios.todos.size)

        vm.aoCancelarExclusao()
        assertNull(vm.uiState.value.erroExclusao)
    }

    @Test
    fun `RF16 - excluir com a senha certa apaga a conta e desconecta`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = contaViewModel()
        vm.aoExcluirConta("milho2026")

        assertTrue(usuarios.todos.isEmpty())
        assertNull(sessao.usuarioId.value)
    }

    // ---------- sessão / navegação ----------

    @Test
    fun `navegacao acompanha entrar e sair`() = runTest {
        val vm = SessaoViewModel(conta)
        assertFalse(vm.logado.value)

        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        assertTrue(vm.logado.value)

        conta.sair()
        assertFalse(vm.logado.value)
    }

    // ---------- alterar senha ----------

    private suspend fun alterarSenhaViewModel(): AlterarSenhaViewModel {
        conta.cadastrar("Fábio Moretti", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        return AlterarSenhaViewModel(conta)
    }

    @Test
    fun `alterar senha valida os campos antes de conferir a atual`() = runTest {
        val vm = alterarSenhaViewModel()
        vm.aoSalvar()

        assertNotNull(vm.uiState.value.erroSenhaAtual)
        assertNotNull(vm.uiState.value.erroNovaSenha)
        assertFalse(vm.uiState.value.concluido)
    }

    @Test
    fun `nova senha com o proprio nome nao passa`() = runTest {
        val vm = alterarSenhaViewModel()
        vm.aoMudarNovaSenha("moretti2026")
        assertFalse(vm.uiState.value.requisitos.naoEhPrevisivel)
    }

    @Test
    fun `senha atual errada aparece no campo dela`() = runTest {
        val vm = alterarSenhaViewModel()
        vm.aoMudarSenhaAtual("errada123")
        vm.aoMudarNovaSenha("colheita2026")
        vm.aoMudarConfirmacao("colheita2026")
        vm.aoSalvar()

        assertEquals("Senha atual incorreta.", vm.uiState.value.erroSenhaAtual)
        assertFalse(vm.uiState.value.concluido)
    }

    @Test
    fun `alterar senha com tudo certo conclui e limpa os campos`() = runTest {
        val vm = alterarSenhaViewModel()
        vm.aoMudarSenhaAtual("milho2026")
        vm.aoMudarNovaSenha("colheita2026")
        vm.aoMudarConfirmacao("colheita2026")
        vm.aoSalvar()

        assertTrue(vm.uiState.value.concluido)
        assertEquals("", vm.uiState.value.novaSenha)
        conta.sair()
        assertTrue(conta.entrar("fabio@exemplo.com", "colheita2026") is ResultadoLogin.Sucesso)
    }
}
