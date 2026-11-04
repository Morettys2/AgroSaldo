package br.joao.agrosaldo.domain

import br.joao.agrosaldo.data.DespachantesDeTeste
import br.joao.agrosaldo.data.RelogioMonotonicoFalso
import br.joao.agrosaldo.data.relogioPorMs
import br.joao.agrosaldo.data.repository.UsuarioRepositoryMemoria
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.data.sessao.TentativasMemoria
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.HashDeSenha
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.conta.ResultadoAlteracaoSenha
import br.joao.agrosaldo.domain.conta.ResultadoCadastro
import br.joao.agrosaldo.domain.conta.ResultadoExclusao
import br.joao.agrosaldo.domain.conta.ResultadoLogin
import br.joao.agrosaldo.domain.model.InstanteMonotonico
import br.joao.agrosaldo.domain.model.TermoDePrivacidade
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras de conta: senha, e-mail, hash, cadastro, login, bloqueio e exclusão.
 */
class ContaTest {

    // Poucas iterações para a suíte ser rápida; algoritmo e formato são os mesmos do app,
    // que usa HashDeSenha.ITERACOES_PADRAO.
    private val hashRapido = HashDeSenha(iteracoes = 1_000)

    private var agora = 1_000_000L
    private val usuarios = UsuarioRepositoryMemoria()
    private val sessao = SessaoMemoria()
    private val tentativas = TentativasMemoria()
    private var trocasDeUsuario = 0

    private val conta = novoContaService()

    /** Nova instância sobre os mesmos dados gravados: simula fechar e reabrir o app. */
    private fun novoContaService() = ContaService(
        usuarios = usuarios,
        sessao = sessao,
        tentativas = tentativas,
        hash = hashRapido,
        relogio = relogioPorMs { agora },
        relogioMonotonico = RelogioMonotonicoIndisponivel,
        despachantes = DespachantesDeTeste(Dispatchers.Unconfined),
        aoTrocarDeUsuario = { trocasDeUsuario++ }
    )

    private var boot = 1
    private var desdeOBoot = 0L

    /** Como o app real: bloqueio medido no relógio monotônico, com [agora] só como reserva após reinício. */
    private fun contaComRelogioMonotonico() = ContaService(
        usuarios = usuarios,
        sessao = sessao,
        tentativas = tentativas,
        hash = hashRapido,
        relogio = relogioPorMs { agora },
        relogioMonotonico = RelogioMonotonicoFalso { InstanteMonotonico(boot, desdeOBoot) },
        despachantes = DespachantesDeTeste(Dispatchers.Unconfined)
    )

    private suspend fun cadastrarMaria(): ResultadoCadastro =
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)

    @Test
    fun `senha precisa de 8 caracteres com letra e numero`() {
        assertFalse(RegrasDeConta.senhaValida("abc123")) // curta
        assertFalse(RegrasDeConta.senhaValida("somenteletras")) // sem número
        assertFalse(RegrasDeConta.senhaValida("12345678")) // sem letra
        assertTrue(RegrasDeConta.senhaValida("milho2026"))
        assertFalse(RegrasDeConta.senhaValida("a1" + "x".repeat(RegrasDeConta.SENHA_MAXIMO)))
    }

    @Test
    fun `requisitos da senha sao marcados um a um`() {
        val soTamanho = RegrasDeConta.requisitosDaSenha("abcdefgh")
        assertTrue(soTamanho.temTamanhoMinimo)
        assertFalse(soTamanho.temLetraENumero)
        assertFalse(soTamanho.todosCumpridos)
        assertTrue(RegrasDeConta.requisitosDaSenha("colheita1").todosCumpridos)
    }

    @Test
    fun `email e normalizado e validado`() {
        assertEquals("maria@exemplo.com", RegrasDeConta.normalizarEmail("  Maria@Exemplo.COM "))
        assertTrue(RegrasDeConta.emailValido("maria@exemplo.com"))
        assertFalse(RegrasDeConta.emailValido("maria@exemplo"))
        assertFalse(RegrasDeConta.emailValido("maria exemplo.com"))
        assertFalse(RegrasDeConta.emailValido(""))
    }

    @Test
    fun `iniciais usam primeiro e ultimo nome`() {
        assertEquals("MS", RegrasDeConta.iniciais("Maria Lima Souza"))
        assertEquals("M", RegrasDeConta.iniciais("márcia"))
        assertEquals("?", RegrasDeConta.iniciais("   "))
    }

    @Test
    fun `hash confere a senha certa e recusa a errada`() {
        val armazenado = hashRapido.gerar("milho2026")
        assertTrue(hashRapido.confere("milho2026", armazenado))
        assertFalse(hashRapido.confere("Milho2026", armazenado))
    }

    @Test
    fun `hash nunca contem a senha e muda a cada vez por causa do sal`() {
        val primeiro = hashRapido.gerar("milho2026")
        val segundo = hashRapido.gerar("milho2026")

        assertFalse(primeiro.contains("milho2026"))
        assertNotEquals(primeiro, segundo)
        assertTrue(primeiro.startsWith("pbkdf2_sha256$1000$"))
    }

    @Test
    fun `hash gravado com outro numero de iteracoes continua valendo`() {
        val antigo = HashDeSenha(iteracoes = 500).gerar("milho2026")
        assertTrue(hashRapido.confere("milho2026", antigo))
    }

    @Test
    fun `hash corrompido conta como senha errada em vez de travar o app`() {
        assertFalse(hashRapido.confere("milho2026", "lixo"))
        assertFalse(hashRapido.confere("milho2026", "pbkdf2_sha256\$abc\$sal\$hash"))
        assertFalse(hashRapido.confere("milho2026", "pbkdf2_sha256$1000$%%%$%%%"))
    }

    @Test
    fun `RF01 - cadastro grava conta com consentimento e ja conecta`() = runTest {
        val resultado = cadastrarMaria()

        assertTrue(resultado is ResultadoCadastro.Sucesso)
        val gravado = usuarios.todos.single()
        assertEquals("maria@exemplo.com", gravado.email)
        assertEquals(Instant.ofEpochMilli(agora), gravado.consentimentoEm)
        assertEquals(TermoDePrivacidade.VERSAO_ATUAL, gravado.versaoTermo)
        assertEquals(gravado.id, sessao.usuarioId.value)
        assertEquals(1, trocasDeUsuario)
    }

    @Test
    fun `senha fica gravada so como hash`() = runTest {
        cadastrarMaria()
        val gravado = usuarios.todos.single()
        assertFalse(gravado.senhaHash.contains("milho2026"))
        assertTrue(hashRapido.confere("milho2026", gravado.senhaHash))
    }

    @Test
    fun `RNF10 - sem aceite do termo nao existe conta`() = runTest {
        val resultado = conta.cadastrar("Maria", "maria@exemplo.com", "milho2026", aceitouTermo = false)

        assertEquals(ResultadoCadastro.TermoNaoAceito, resultado)
        assertTrue(usuarios.todos.isEmpty())
        assertNull(sessao.usuarioId.value)
    }

    @Test
    fun `mesmo email com maiusculas nao cria segunda conta`() = runTest {
        cadastrarMaria()
        conta.sair()

        val segundo = conta.cadastrar("Outro", " MARIA@exemplo.com", "outra2026", aceitouTermo = true)

        assertEquals(ResultadoCadastro.EmailJaCadastrado, segundo)
        assertEquals(1, usuarios.todos.size)
    }

    @Test
    fun `cadastro recusa senha fraca mesmo que a tela deixe passar`() = runTest {
        val resultado = conta.cadastrar("Maria", "maria@exemplo.com", "123", aceitouTermo = true)
        assertTrue(resultado is ResultadoCadastro.DadosInvalidos)
        assertTrue(usuarios.todos.isEmpty())
    }

    @Test
    fun `RF02 - login com a senha certa conecta`() = runTest {
        cadastrarMaria()
        conta.sair()

        val resultado = conta.entrar("Maria@Exemplo.com ", "milho2026")

        assertTrue(resultado is ResultadoLogin.Sucesso)
        assertEquals(usuarios.todos.single().id, sessao.usuarioId.value)
    }

    @Test
    fun `senha errada e email inexistente dao a mesma resposta`() = runTest {
        cadastrarMaria()
        conta.sair()

        assertEquals(ResultadoLogin.CredenciaisInvalidas, conta.entrar("maria@exemplo.com", "errada123"))
        assertEquals(ResultadoLogin.CredenciaisInvalidas, conta.entrar("ninguem@exemplo.com", "milho2026"))
        assertNull(sessao.usuarioId.value)
    }

    @Test
    fun `cinco erros seguidos bloqueiam por 30 segundos`() = runTest {
        cadastrarMaria()
        conta.sair()

        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.entrar("maria@exemplo.com", "errada123") }

        // Nem a senha certa entra durante o bloqueio.
        val bloqueado = conta.entrar("maria@exemplo.com", "milho2026")
        assertEquals(ResultadoLogin.Bloqueado(segundosRestantes = 30), bloqueado)
        assertNull(sessao.usuarioId.value)

        agora += 29_500
        assertEquals(ResultadoLogin.Bloqueado(segundosRestantes = 1), conta.entrar("maria@exemplo.com", "milho2026"))

        agora += 500
        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Sucesso)
    }

    @Test
    fun `a senha errada que bloqueia ja avisa o bloqueio`() = runTest {
        cadastrarMaria()
        conta.sair()

        repeat(ContaService.MAXIMO_TENTATIVAS - 1) {
            assertEquals(ResultadoLogin.CredenciaisInvalidas, conta.entrar("maria@exemplo.com", "errada123"))
        }

        assertEquals(ResultadoLogin.Bloqueado(segundosRestantes = 30), conta.entrar("maria@exemplo.com", "errada123"))
    }

    @Test
    fun `troca de senha e exclusao tambem avisam no erro que bloqueia`() = runTest {
        cadastrarMaria()
        repeat(ContaService.MAXIMO_TENTATIVAS - 1) {
            assertEquals(ResultadoAlteracaoSenha.SenhaAtualIncorreta, conta.alterarSenha("errada123", "colheita2026"))
        }
        assertEquals(ResultadoAlteracaoSenha.Bloqueado(30), conta.alterarSenha("errada123", "colheita2026"))

        agora += ContaService.BLOQUEIO_MS
        // Já passou do limite: o próximo erro bloqueia de novo, agora pelo dobro.
        assertEquals(ResultadoExclusao.Bloqueado(60), conta.excluirConta("errada123"))
    }

    @Test
    fun `fechar e abrir o app nao zera o bloqueio`() = runTest {
        cadastrarMaria()
        conta.sair()
        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.entrar("maria@exemplo.com", "errada123") }

        val depoisDeReabrir = novoContaService()

        assertTrue(depoisDeReabrir.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Bloqueado)
    }

    @Test
    fun `cada erro depois do bloqueio dobra a espera ate 15 minutos`() = runTest {
        cadastrarMaria()
        conta.sair()
        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.entrar("maria@exemplo.com", "errada123") }

        val esperas = mutableListOf(30L)
        repeat(6) {
            agora += 16 * 60_000L // Passa do bloqueio anterior (máximo de 15 min).
            conta.entrar("maria@exemplo.com", "errada123")
            val r = conta.entrar("maria@exemplo.com", "errada123") as ResultadoLogin.Bloqueado
            esperas += r.segundosRestantes
        }

        assertEquals(listOf(30L, 60L, 120L, 240L, 480L, 900L, 900L), esperas)
    }

    @Test
    fun `atrasar o relogio do celular nao cria bloqueio de meses`() = runTest {
        cadastrarMaria()
        conta.sair()
        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.entrar("maria@exemplo.com", "errada123") }

        agora -= 365L * 24 * 60 * 60 * 1000 // Relógio do aparelho voltado um ano.

        val r = conta.entrar("maria@exemplo.com", "milho2026") as ResultadoLogin.Bloqueado
        assertEquals(ContaService.BLOQUEIO_MAXIMO_MS / 1000, r.segundosRestantes)
    }

    @Test
    fun `relogio adiantado ao errar nao trava a conta alem do maximo`() = runTest {
        cadastrarMaria()
        conta.sair()
        val dezAnos = 10L * 365 * 24 * 60 * 60 * 1000

        agora += dezAnos
        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.entrar("maria@exemplo.com", "errada123") }
        agora -= dezAnos

        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Bloqueado)
        agora += ContaService.BLOQUEIO_MAXIMO_MS
        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Sucesso)
    }

    @Test
    fun `adiantar o relogio do celular nao encerra o bloqueio`() = runTest {
        val conta = contaComRelogioMonotonico()
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        conta.sair()
        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.entrar("maria@exemplo.com", "errada123") }

        agora += 24 * 60 * 60 * 1000L

        assertEquals(ResultadoLogin.Bloqueado(segundosRestantes = 30), conta.entrar("maria@exemplo.com", "milho2026"))
    }

    @Test
    fun `bloqueio termina no tempo certo mesmo com o relogio do celular atrasado`() = runTest {
        val conta = contaComRelogioMonotonico()
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        conta.sair()
        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.entrar("maria@exemplo.com", "errada123") }

        agora -= 24 * 60 * 60 * 1000L
        desdeOBoot += 30_000

        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Sucesso)
    }

    @Test
    fun `depois de reiniciar o celular a espera recomeca inteira e nunca encurta`() = runTest {
        val conta = contaComRelogioMonotonico()
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        conta.sair()
        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.entrar("maria@exemplo.com", "errada123") }

        // Reinício: o contador monotônico recomeça e o identificador do boot muda.
        boot += 1
        desdeOBoot = 0L
        agora += 10_000

        assertEquals(ResultadoLogin.Bloqueado(segundosRestantes = 30), conta.entrar("maria@exemplo.com", "milho2026"))
        desdeOBoot += 30_000
        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Sucesso)
    }

    @Test
    fun `reiniciar o celular e adiantar a hora nao encerra o bloqueio`() = runTest {
        val conta = contaComRelogioMonotonico()
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        conta.sair()
        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.entrar("maria@exemplo.com", "errada123") }

        boot += 1
        desdeOBoot = 0L
        agora += 24 * 60 * 60 * 1000L

        assertEquals(ResultadoLogin.Bloqueado(segundosRestantes = 30), conta.entrar("maria@exemplo.com", "milho2026"))
        // Reiniciar de novo também não encurta: a espera recomeça no boot novo.
        boot += 1
        desdeOBoot = 0L
        assertEquals(ResultadoLogin.Bloqueado(segundosRestantes = 30), conta.entrar("maria@exemplo.com", "milho2026"))
        desdeOBoot += 30_000
        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Sucesso)
    }

    @Test
    fun `excluir uma de duas contas avisa que ainda restam contas no aparelho`() = runTest {
        val ultimas = mutableListOf<Boolean>()
        val conta = ContaService(
            usuarios = usuarios,
            sessao = sessao,
            tentativas = tentativas,
            hash = hashRapido,
            relogio = relogioPorMs { agora },
            relogioMonotonico = RelogioMonotonicoIndisponivel,
            despachantes = DespachantesDeTeste(Dispatchers.Unconfined),
            aoExcluirConta = { _, ultimaConta -> ultimas += ultimaConta }
        )
        conta.cadastrar("José Lima", "jose@exemplo.com", "trigo2026", aceitouTermo = true)
        conta.sair()
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)

        conta.excluirConta("milho2026")
        assertEquals("ainda resta a conta do José", listOf(false), ultimas)

        conta.entrar("jose@exemplo.com", "trigo2026")
        conta.excluirConta("trigo2026")
        assertEquals("a última conta saiu", listOf(false, true), ultimas)
    }

    @Test
    fun `senha errada na exclusao conta como tentativa`() = runTest {
        cadastrarMaria()
        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.excluirConta("errada123") }

        assertTrue(conta.excluirConta("milho2026") is ResultadoExclusao.Bloqueado)
        assertEquals(1, usuarios.todos.size)
    }

    @Test
    fun `acerto zera a contagem de erros`() = runTest {
        cadastrarMaria()
        conta.sair()

        repeat(4) { conta.entrar("maria@exemplo.com", "errada123") }
        conta.entrar("maria@exemplo.com", "milho2026")
        conta.sair()
        repeat(4) { conta.entrar("maria@exemplo.com", "errada123") }

        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Sucesso)
    }

    private suspend fun cadastrarIntrusoESair() {
        conta.cadastrar("Intruso", "intruso@exemplo.com", "trigo2026", aceitouTermo = true)
        conta.sair()
    }

    @Test
    fun `entrar na propria conta nao zera os erros no email de outra pessoa`() = runTest {
        cadastrarMaria()
        conta.sair()
        cadastrarIntrusoESair()

        // Quem pega o celular erra no e-mail do dono e entra na própria conta, repetidas vezes.
        repeat(ContaService.MAXIMO_TENTATIVAS - 1) { conta.entrar("maria@exemplo.com", "errada123") }
        assertTrue(conta.entrar("intruso@exemplo.com", "trigo2026") is ResultadoLogin.Sucesso)
        conta.sair()
        conta.entrar("maria@exemplo.com", "errada123")

        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Bloqueado)
    }

    @Test
    fun `erros no email de uma conta nao bloqueiam outra`() = runTest {
        cadastrarMaria()
        conta.sair()
        cadastrarIntrusoESair()

        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.entrar("maria@exemplo.com", "errada123") }

        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Bloqueado)
        assertTrue(conta.entrar("intruso@exemplo.com", "trigo2026") is ResultadoLogin.Sucesso)
    }

    @Test
    fun `email digitado de outro jeito conta no mesmo contador`() = runTest {
        cadastrarMaria()
        conta.sair()

        repeat(ContaService.MAXIMO_TENTATIVAS) { i ->
            conta.entrar(if (i % 2 == 0) " MARIA@exemplo.com" else "maria@Exemplo.com ", "errada123")
        }

        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Bloqueado)
    }

    @Test
    fun `contador guarda so um resumo do email, nunca o email`() = runTest {
        cadastrarMaria()
        conta.sair()
        conta.entrar("maria@exemplo.com", "errada123")

        val chave = tentativas.chaves.single()
        assertFalse(chave.contains("maria"))
        assertTrue(chave.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `acerto apaga o registro de tentativas do email`() = runTest {
        cadastrarMaria()
        conta.sair()
        repeat(2) { conta.entrar("maria@exemplo.com", "errada123") }

        conta.entrar("maria@exemplo.com", "milho2026")

        assertTrue(tentativas.chaves.isEmpty())
    }

    @Test
    fun `erros na troca de senha bloqueiam o login da mesma conta`() = runTest {
        cadastrarMaria()
        repeat(ContaService.MAXIMO_TENTATIVAS) { conta.alterarSenha("errada123", "colheita2026") }
        conta.sair()

        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Bloqueado)
    }

    @Test
    fun `login refaz o hash antigo com o custo atual sem a pessoa perceber`() = runTest {
        val antigo = ContaService(
            usuarios,
            sessao,
            tentativas,
            hash = HashDeSenha(iteracoes = 500),
            relogio = relogioPorMs { agora },
            relogioMonotonico = RelogioMonotonicoIndisponivel,
            despachantes = DespachantesDeTeste(Dispatchers.Unconfined)
        )
        antigo.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        antigo.sair()
        assertTrue(usuarios.todos.single().senhaHash.startsWith("pbkdf2_sha256\$500\$"))

        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Sucesso)

        assertTrue(usuarios.todos.single().senhaHash.startsWith("pbkdf2_sha256\$1000\$"))
        conta.sair()
        assertTrue(conta.entrar("maria@exemplo.com", "milho2026") is ResultadoLogin.Sucesso)
    }

    @Test
    fun `hash ficticio custa o mesmo que um real e nunca confere`() {
        val real = hashRapido.gerar("milho2026").split("$")
        val ficticio = hashRapido.ficticio()
        val partes = ficticio.split("$")

        // Mesmo algoritmo, mesmas iterações e mesmos tamanhos de sal e hash: conferir roda o PBKDF2 inteiro.
        assertEquals(real.size, partes.size)
        assertEquals(real[0], partes[0])
        assertEquals(real[1], partes[1])
        assertEquals(real[2].length, partes[2].length)
        assertEquals(real[3].length, partes[3].length)
        assertFalse(hashRapido.precisaAtualizar(ficticio))
        assertFalse(hashRapido.confere("milho2026", ficticio))
        assertFalse(hashRapido.confere("", ficticio))
        assertNotEquals(ficticio, hashRapido.ficticio())
    }

    @Test
    fun `hash so pede atualizacao quando o custo gravado e menor`() {
        assertTrue(hashRapido.precisaAtualizar(HashDeSenha(iteracoes = 500).gerar("x1234567")))
        assertFalse(hashRapido.precisaAtualizar(hashRapido.gerar("x1234567")))
        assertFalse(hashRapido.precisaAtualizar("lixo"))
    }

    @Test
    fun `mesma senha com acento digitado de outro jeito confere`() {
        // "ç" pré-composto (U+00E7) vs. "c" + cedilha combinante (U+0327).
        val armazenado = hashRapido.gerar("caf\u00e71234")
        assertTrue(hashRapido.confere("cafc\u03271234", armazenado))
    }

    @Test
    fun `app padrao usa o custo recomendado pela OWASP`() {
        assertEquals(600_000, HashDeSenha.ITERACOES_PADRAO)
    }

    @Test
    fun `senhas de vazamento e com dados da pessoa sao recusadas`() {
        assertFalse(RegrasDeConta.senhaValida("senha123"))
        assertFalse(RegrasDeConta.senhaValida("Brasil123"))
        assertFalse(RegrasDeConta.senhaValida("agrosaldo1"))
        assertFalse(RegrasDeConta.senhaValida("aaaaaaa1"))
        assertFalse(RegrasDeConta.senhaValida("12345678a")) // sequência
        assertFalse(RegrasDeConta.senhaValida("abcdef12")) // sequência
        assertFalse(RegrasDeConta.senhaValida("senha1234!")) // variação da lista
        assertFalse(RegrasDeConta.senhaValida("987654zz")) // sequência descendo
        assertFalse(RegrasDeConta.senhaValida("souza2026", email = "x@y.com", nome = "Maria Souza"))
        assertFalse(RegrasDeConta.senhaValida("maria2026x", email = "maria@exemplo.com"))
        assertTrue(RegrasDeConta.senhaValida("colheita2026", email = "maria@exemplo.com", nome = "Maria Souza"))
    }

    @Test
    fun `requisito de senha previsivel so fica verde depois de digitar`() {
        assertFalse(RegrasDeConta.requisitosDaSenha("").naoEhPrevisivel)
        assertTrue(RegrasDeConta.requisitosDaSenha("colheita2026").naoEhPrevisivel)
        assertFalse(RegrasDeConta.requisitosDaSenha("12345678").naoEhPrevisivel)
    }

    @Test
    fun `trocar senha exige a atual e passa a valer a nova`() = runTest {
        cadastrarMaria()

        assertEquals(ResultadoAlteracaoSenha.SenhaAtualIncorreta, conta.alterarSenha("errada123", "colheita2026"))
        assertEquals(ResultadoAlteracaoSenha.NovaIgualAAtual, conta.alterarSenha("milho2026", "milho2026"))
        assertEquals(ResultadoAlteracaoSenha.NovaNaoCumpreRequisitos, conta.alterarSenha("milho2026", "senha123"))
        assertEquals(ResultadoAlteracaoSenha.Sucesso, conta.alterarSenha("milho2026", "colheita2026"))

        conta.sair()
        assertEquals(ResultadoLogin.CredenciaisInvalidas, conta.entrar("maria@exemplo.com", "milho2026"))
        assertTrue(conta.entrar("maria@exemplo.com", "colheita2026") is ResultadoLogin.Sucesso)
    }

    @Test
    fun `trocar senha sem ninguem conectado nao faz nada`() = runTest {
        assertEquals(ResultadoAlteracaoSenha.SemSessao, conta.alterarSenha("a", "colheita2026"))
    }

    @Test
    fun `so a sessao aberta pelo cadastro conta como de conta nova`() = runTest {
        assertFalse(conta.sessaoDeContaNova.value)
        cadastrarMaria()
        assertTrue(conta.sessaoDeContaNova.value)

        conta.esquecerContaNova()
        assertFalse(conta.sessaoDeContaNova.value)

        cadastrarIntrusoESair()
        assertFalse(conta.sessaoDeContaNova.value)
        conta.entrar("maria@exemplo.com", "milho2026")
        assertFalse(conta.sessaoDeContaNova.value)
    }

    @Test
    fun `sair encerra a sessao e avisa a troca de usuario`() = runTest {
        cadastrarMaria()
        conta.sair()

        assertNull(sessao.usuarioId.value)
        assertEquals(2, trocasDeUsuario)
        assertNull(conta.observarUsuarioLogado().first())
    }

    @Test
    fun `excluir conta pede a senha`() = runTest {
        cadastrarMaria()

        assertEquals(ResultadoExclusao.SenhaIncorreta, conta.excluirConta("errada123"))
        assertEquals(1, usuarios.todos.size)
        assertTrue(conta.estaLogado())
    }

    @Test
    fun `excluir conta apaga o usuario e desconecta`() = runTest {
        cadastrarMaria()

        assertEquals(ResultadoExclusao.Sucesso, conta.excluirConta("milho2026"))

        assertTrue(usuarios.todos.isEmpty())
        assertFalse(conta.estaLogado())
    }

    private fun contaQueLimpaAoExcluir(aoExcluirConta: suspend (String) -> Unit) = ContaService(
        usuarios = usuarios,
        sessao = sessao,
        tentativas = tentativas,
        hash = hashRapido,
        relogio = relogioPorMs { agora },
        relogioMonotonico = RelogioMonotonicoIndisponivel,
        despachantes = DespachantesDeTeste(Dispatchers.Unconfined),
        aoExcluirConta = { usuarioId, _ -> aoExcluirConta(usuarioId) }
    )

    @Test
    fun `excluir conta limpa o que ficou guardado dela fora do banco`() = runTest {
        val limpas = mutableListOf<String>()
        var contasNoBancoNaLimpeza = -1
        var conectadoNaLimpeza = false
        val conta = contaQueLimpaAoExcluir { id ->
            limpas += id
            contasNoBancoNaLimpeza = usuarios.todos.size
            conectadoNaLimpeza = sessao.usuarioId.value != null
        }
        val criada = conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)
        val id = (criada as ResultadoCadastro.Sucesso).usuario.id

        conta.excluirConta("errada123")
        assertTrue(limpas.isEmpty())

        assertEquals(ResultadoExclusao.Sucesso, conta.excluirConta("milho2026"))
        assertEquals(listOf(id), limpas)
        assertEquals(0, contasNoBancoNaLimpeza)
        // Limpa antes de encerrar a sessão: a tela segue em "excluindo" até o fim.
        assertTrue(conectadoNaLimpeza)
        assertNull(sessao.usuarioId.value)
        assertTrue(tentativas.chaves.isEmpty())
    }

    @Test
    fun `falha na limpeza nao deixa a sessao presa a conta apagada`() = runTest {
        val conta = contaQueLimpaAoExcluir { error("disco cheio") }
        conta.cadastrar("Maria Souza", "maria@exemplo.com", "milho2026", aceitouTermo = true)

        assertTrue(runCatching { conta.excluirConta("milho2026") }.isFailure)

        assertTrue(usuarios.todos.isEmpty())
        assertNull(sessao.usuarioId.value)
    }

    @Test
    fun `sessao que aponta para conta apagada e encerrada ao abrir o app`() = runTest {
        sessao.iniciar("conta-que-nao-existe")
        conta.validarSessao()
        assertNull(sessao.usuarioId.value)
    }

    @Test
    fun `observar usuario logado acompanha a sessao`() = runTest {
        assertNull(conta.observarUsuarioLogado().first())
        cadastrarMaria()
        assertEquals("Maria Souza", conta.observarUsuarioLogado().first()?.nome)
    }
}
