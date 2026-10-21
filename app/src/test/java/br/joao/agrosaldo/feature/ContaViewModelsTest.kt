package br.joao.agrosaldo.feature

import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.core.navigation.SessaoViewModel
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.DespachantesDeTeste
import br.joao.agrosaldo.data.RelogioMonotonicoFalso
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.relogioEm
import br.joao.agrosaldo.data.relogioFixo
import br.joao.agrosaldo.data.relogioPorMs
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.data.repository.UsuarioRepositoryMemoria
import br.joao.agrosaldo.data.sessao.BloqueioMemoria
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.data.sessao.TentativasMemoria
import br.joao.agrosaldo.domain.RelogioMonotonico
import br.joao.agrosaldo.domain.RelogioMonotonicoIndisponivel
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.HashDeSenha
import br.joao.agrosaldo.domain.conta.ResultadoCadastro
import br.joao.agrosaldo.domain.conta.ResultadoLogin
import br.joao.agrosaldo.domain.model.InstanteMonotonico
import br.joao.agrosaldo.domain.model.TermoDePrivacidade
import br.joao.agrosaldo.domain.model.Usuario
import br.joao.agrosaldo.domain.repository.BloqueioRepository
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import br.joao.agrosaldo.feature.conta.AlterarSenhaViewModel
import br.joao.agrosaldo.feature.conta.CadastroViewModel
import br.joao.agrosaldo.feature.conta.ContaViewModel
import br.joao.agrosaldo.feature.conta.LoginViewModel
import br.joao.agrosaldo.feature.conta.NovoConsentimentoViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * ViewModels de cadastro, login, conta, sessão/bloqueio, novo aceite e alteração de senha.
 *
 * Usa o [ContaService] real sobre repositórios em memória.
 */
@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle e runCurrent
class ContaViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val usuarios = UsuarioRepositoryMemoria()
    private val sessao = SessaoMemoria()
    private val conta = ContaService(
        usuarios = usuarios,
        sessao = sessao,
        tentativas = TentativasMemoria(),
        // Poucas iterações só para o teste ser rápido.
        hash = HashDeSenha(iteracoes = 1_000),
        // 30/09/2026 22:40 UTC.
        relogio = relogioPorMs { 1_790_808_000_000L },
        despachantes = DespachantesDeTeste(Dispatchers.Unconfined),
        relogioMonotonico = RelogioMonotonicoIndisponivel
    )

    private val propriedades = PropriedadeRepositoryMemoria()
    private val selecao = SelecaoAtual(relogio = relogioEm(LocalDate.of(2026, 9, 30)))

    private fun sessaoViewModel(
        conta: ContaService = this.conta,
        bloqueio: BloqueioRepository = BloqueioMemoria(),
        relogio: RelogioMonotonico = RelogioMonotonicoIndisponivel
    ) = SessaoViewModel(conta, bloqueio, propriedades, relogio, selecao)

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
        repeat(ContaService.MAXIMO_TENTATIVAS - 1) {
            vm.aoMudarSenha("errada123")
            vm.aoEntrar()
            assertEquals("E-mail ou senha incorretos.", vm.uiState.value.erro)
        }

        vm.aoMudarSenha("errada123")
        vm.aoEntrar()

        assertEquals("Muitas tentativas. Tente de novo em 30 segundos.", vm.uiState.value.erro)
    }

    private val bloqueio = BloqueioMemoria()
    private fun contaViewModel() =
        ContaViewModel(conta, PropriedadeRepositoryMemoria(), bloqueio, relogio = relogioFixo(Instant.EPOCH, ZoneOffset.UTC))

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

    @Test
    fun `navegacao acompanha entrar e sair`() = runTest {
        val vm = sessaoViewModel()
        assertFalse(vm.logado.value)

        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        assertTrue(vm.logado.value)

        conta.sair()
        assertFalse(vm.logado.value)
    }

    @Test
    fun `tela de conta liga e desliga o bloqueio`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = contaViewModel()
        assertFalse(vm.uiState.value.bloqueioAtivo)

        vm.aoDefinirBloqueio(true)
        assertTrue(bloqueio.ativo.value)
        assertTrue(vm.uiState.value.bloqueioAtivo)

        vm.aoDefinirBloqueio(false)
        assertFalse(vm.uiState.value.bloqueioAtivo)
    }

    private var agora = 1_790_808_000_000L

    private fun sessaoComBloqueio(ativo: Boolean = true) = sessaoViewModel(
        bloqueio = BloqueioMemoria(ativo),
        relogio = RelogioMonotonicoFalso {
            InstanteMonotonico(1, agora)
        }
    )

    @Test
    fun `abrir o app com sessao gravada e bloqueio ligado pede a digital`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = sessaoComBloqueio()

        assertTrue(vm.precisaDesbloquear.value)
        vm.aoDesbloquear()
        assertFalse(vm.precisaDesbloquear.value)
    }

    @Test
    fun `com o bloqueio desligado nunca trava`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = sessaoComBloqueio(ativo = false)
        vm.aoIrParaSegundoPlano()
        agora += 10 * 60_000L
        vm.aoVoltarParaPrimeiroPlano()
        assertFalse(vm.precisaDesbloquear.value)
    }

    @Test
    fun `entrar com e-mail e senha ja conta como desbloqueado`() = runTest {
        contaCriadaESaiu()
        val vm = sessaoComBloqueio()
        assertFalse(vm.precisaDesbloquear.value)

        conta.entrar("fabio@exemplo.com", "milho2026")

        assertTrue(vm.logado.value)
        assertFalse(vm.precisaDesbloquear.value)
    }

    @Test
    fun `trava de novo depois de 2 minutos fora, nao antes`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = sessaoComBloqueio()
        vm.aoDesbloquear()

        vm.aoIrParaSegundoPlano()
        agora += 119_000L
        vm.aoVoltarParaPrimeiroPlano()
        assertFalse(vm.precisaDesbloquear.value)

        vm.aoIrParaSegundoPlano()
        agora += SessaoViewModel.TEMPO_PARA_TRAVAR_MS
        vm.aoVoltarParaPrimeiroPlano()
        assertTrue(vm.precisaDesbloquear.value)
    }

    @Test
    fun `digitar o PIN numa tela do sistema nao trava de novo`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = sessaoComBloqueio()

        // A tela de PIN do sistema põe o app em segundo plano; o desbloqueio chega antes
        // de o app voltar ao primeiro plano, mesmo depois do tempo de travar.
        vm.aoIrParaSegundoPlano()
        agora += 3 * 60_000L
        vm.aoDesbloquear()
        vm.aoVoltarParaPrimeiroPlano()

        assertFalse(vm.precisaDesbloquear.value)
    }

    @Test
    fun `ligar o bloqueio na tela de conta nao trava na cara da pessoa`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val bloqueioDoApp = BloqueioMemoria(false)
        val vm = sessaoViewModel(bloqueio = bloqueioDoApp, relogio = RelogioMonotonicoFalso { InstanteMonotonico(1, agora) })

        bloqueioDoApp.definir(true)

        assertFalse(vm.precisaDesbloquear.value)
    }

    @Test
    fun `quem acabou de criar a conta recebe a oferta do bloqueio`() = runTest {
        val vm = sessaoViewModel()
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        assertTrue(vm.oferecerBloqueio.value)
    }

    @Test
    fun `entrar com a senha nao oferece o bloqueio`() = runTest {
        contaCriadaESaiu()
        val vm = sessaoViewModel()
        conta.entrar("fabio@exemplo.com", "milho2026")
        assertFalse(vm.oferecerBloqueio.value)
    }

    @Test
    fun `agora nao dispensa a oferta sem ligar o bloqueio`() = runTest {
        val bloqueioDoApp = BloqueioMemoria(false)
        val vm = sessaoViewModel(bloqueio = bloqueioDoApp)
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)

        vm.aoDispensarOfertaDeBloqueio()

        assertFalse(vm.oferecerBloqueio.value)
        assertFalse(bloqueioDoApp.ativo.value)
    }

    @Test
    fun `ligar pela oferta liga o bloqueio sem travar na hora`() = runTest {
        val bloqueioDoApp = BloqueioMemoria(false)
        val vm = sessaoViewModel(bloqueio = bloqueioDoApp)
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)

        vm.aoLigarBloqueioPelaOferta()

        assertTrue(bloqueioDoApp.ativo.value)
        assertFalse(vm.oferecerBloqueio.value)
        assertFalse(vm.precisaDesbloquear.value)
        // Desligar depois, na tela de Conta, não traz a oferta de volta.
        bloqueioDoApp.definir(false)
        assertFalse(vm.oferecerBloqueio.value)
    }

    @Test
    fun `sem oferta com o bloqueio ja ligado ou depois de sair`() = runTest {
        val vm = sessaoViewModel(bloqueio = BloqueioMemoria(true))
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        assertFalse(vm.oferecerBloqueio.value)

        val outro = sessaoViewModel()
        conta.sair()
        assertFalse(outro.oferecerBloqueio.value)
    }

    @Test
    fun `usar a senha na tela de bloqueio sai da conta`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = sessaoComBloqueio()

        vm.aoUsarSenha()

        assertFalse(vm.logado.value)
        assertFalse(vm.precisaDesbloquear.value)
    }

    private suspend fun contaQueAceitouVersao1(): String {
        val criada = conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val id = (criada as ResultadoCadastro.Sucesso).usuario.id
        usuarios.registrarConsentimento(id, Instant.ofEpochMilli(1_000L), versao = 1)
        return id
    }

    @Test
    fun `conta nova ja nasce com a versao atual e nao pede aceite`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = sessaoViewModel()
        assertEquals(false, vm.precisaNovoAceite.value)
    }

    @Test
    fun `quem aceitou a versao antiga precisa aceitar de novo`() = runTest {
        contaQueAceitouVersao1()
        val vm = sessaoViewModel()
        assertEquals(true, vm.precisaNovoAceite.value)
    }

    @Test
    fun `aceitar a politica nova grava a versao e a data`() = runTest {
        val id = contaQueAceitouVersao1()
        val sessaoVm = sessaoViewModel()
        val vm = NovoConsentimentoViewModel(conta)

        vm.aoAceitar()

        assertEquals(false, sessaoVm.precisaNovoAceite.value)
        val gravado = usuarios.buscarPorId(id)
        assertEquals(TermoDePrivacidade.VERSAO_ATUAL, gravado?.versaoTermo)
        assertEquals(Instant.ofEpochMilli(1_790_808_000_000L), gravado?.consentimentoEm)
        assertFalse(vm.uiState.value.processando)
        assertNull(vm.uiState.value.erro)
    }

    /** Só entrega a conta depois de [liberado]: simula a leitura do banco ainda em curso. */
    private class UsuariosDemorados(private val real: UsuarioRepository) : UsuarioRepository by real {
        val liberado = MutableStateFlow(false)

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observar(id: String): Flow<Usuario?> = liberado.filter { it }.flatMapLatest { real.observar(id) }
    }

    private fun contaSobre(repositorio: UsuarioRepository) = ContaService(
        usuarios = repositorio,
        sessao = sessao,
        tentativas = TentativasMemoria(),
        hash = HashDeSenha(iteracoes = 1_000),
        relogio = relogioPorMs { 1_790_808_000_000L },
        despachantes = DespachantesDeTeste(Dispatchers.Unconfined),
        relogioMonotonico = RelogioMonotonicoIndisponivel
    )

    @Test
    fun `antes de ler a conta nao se sabe se a politica precisa de novo aceite`() = runTest {
        contaQueAceitouVersao1()
        val demorados = UsuariosDemorados(usuarios)
        val vm = sessaoViewModel(contaSobre(demorados))

        assertNull(vm.precisaNovoAceite.value)

        demorados.liberado.value = true
        assertEquals(true, vm.precisaNovoAceite.value)
    }

    @Test
    fun `sem ninguem conectado nao ha aceite pendente`() = runTest {
        val vm = sessaoViewModel(contaSobre(UsuariosDemorados(usuarios)))
        assertEquals(false, vm.precisaNovoAceite.value)
    }

    @Test
    fun `entrar em outra conta volta a nao saber ate ler a conta nova`() = runTest {
        val demorados = UsuariosDemorados(usuarios).apply { liberado.value = true }
        val conta = contaSobre(demorados)
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        conta.sair()
        val vm = sessaoViewModel(conta)

        demorados.liberado.value = false
        conta.entrar("fabio@exemplo.com", "milho2026")
        assertNull(vm.precisaNovoAceite.value)

        demorados.liberado.value = true
        assertEquals(false, vm.precisaNovoAceite.value)
    }

    @Test
    fun `notificacao nao abre o clima com novo aceite pendente`() = runTest {
        contaQueAceitouVersao1()
        assertFalse(sessaoViewModel().prepararAberturaDoClima(null))
    }

    @Test
    fun `notificacao abre o clima com sessao e aceite em dia`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        assertTrue(sessaoViewModel().prepararAberturaDoClima(null))
    }

    @Test
    fun `notificacao sem ninguem conectado e ignorada`() = runTest {
        assertFalse(sessaoViewModel().prepararAberturaDoClima(null))
    }

    @Test
    fun `notificacao seleciona a propriedade do aviso antes de abrir o clima`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = sessaoViewModel()

        assertTrue(vm.prepararAberturaDoClima(DadosDeExemplo.ID_SITIO))
        assertEquals(DadosDeExemplo.ID_SITIO, selecao.propriedadeId.value)
    }

    @Test
    fun `pedido com propriedade que nao e do usuario e ignorado`() = runTest {
        conta.cadastrar("Fábio", "fabio@exemplo.com", "milho2026", aceitouTermo = true)
        val vm = sessaoViewModel()

        // O repositório real só encontra propriedades do usuário conectado.
        assertFalse(vm.prepararAberturaDoClima("propriedade-de-outra-conta"))
        assertNull(selecao.propriedadeId.value)
    }

    @Test
    fun `aceite pendente nao deixa o pedido trocar a propriedade`() = runTest {
        contaQueAceitouVersao1()
        assertFalse(sessaoViewModel().prepararAberturaDoClima(DadosDeExemplo.ID_SITIO))
        assertNull(selecao.propriedadeId.value)
    }

    @Test
    fun `notificacao espera a conta ser lida antes de decidir`() = runTest {
        contaQueAceitouVersao1()
        val demorados = UsuariosDemorados(usuarios)
        val vm = sessaoViewModel(contaSobre(demorados))

        val decisao = async { vm.prepararAberturaDoClima(null) }
        runCurrent()
        assertFalse(decisao.isCompleted)

        demorados.liberado.value = true
        assertFalse(decisao.await())
    }

    @Test
    fun `recusar a politica nova sai da conta sem apagar nada`() = runTest {
        contaQueAceitouVersao1()
        val vm = NovoConsentimentoViewModel(conta)

        vm.aoRecusar()

        assertNull(sessao.usuarioId.value)
        assertEquals(1, usuarios.todos.size)
        assertEquals(1, usuarios.todos.single().versaoTermo)
    }

    @Test
    fun `sem ninguem conectado o aceite nao grava e mostra erro`() = runTest {
        val vm = NovoConsentimentoViewModel(conta)
        vm.aoAceitar()
        assertNotNull(vm.uiState.value.erro)
    }

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
    fun `nova senha com o proprio nome nao passa e o campo mostra o erro`() = runTest {
        val vm = alterarSenhaViewModel()
        vm.aoMudarSenhaAtual("milho2026")
        vm.aoMudarNovaSenha("moretti2026")
        vm.aoMudarConfirmacao("moretti2026")
        assertFalse(vm.uiState.value.requisitos.naoEhPrevisivel)

        vm.aoSalvar()

        assertEquals("A nova senha não cumpre os requisitos abaixo.", vm.uiState.value.erroNovaSenha)
        assertFalse(vm.uiState.value.concluido)
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
