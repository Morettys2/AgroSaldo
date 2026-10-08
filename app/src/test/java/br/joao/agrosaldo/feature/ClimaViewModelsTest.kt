package br.joao.agrosaldo.feature

import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.clima.ApoioClima.AGORA
import br.joao.agrosaldo.clima.ApoioClima.previsao
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.preferencias.AgendaFalsa
import br.joao.agrosaldo.data.preferencias.AjustesAvisoMemoria
import br.joao.agrosaldo.data.repository.ClimaRepositoryMemoria
import br.joao.agrosaldo.data.repository.LancamentoRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.domain.clima.CondicaoTempo
import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.repository.AjustesAviso
import br.joao.agrosaldo.feature.clima.AjustesAvisoViewModel
import br.joao.agrosaldo.feature.clima.ClimaViewModel
import br.joao.agrosaldo.feature.inicio.InicioViewModel
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** ClimaViewModel, AjustesAvisoViewModel e o cartão de chuva do InicioViewModel. */
class ClimaViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fazendaId = DadosDeExemplo.ID_FAZENDA
    private val comChuva = previsao(mapOf(18 to (85 to 6.0)))

    @Before
    fun limparSelecao() {
        SelecaoAtual.limpar()
    }

    private fun climaViewModel(
        clima: ClimaRepositoryMemoria,
        ajustes: AjustesAvisoMemoria = AjustesAvisoMemoria(),
        propriedades: PropriedadeRepositoryMemoria = PropriedadeRepositoryMemoria()
    ) = ClimaViewModel(propriedades, clima, ajustes, relogio = { AGORA.toEpochMilli() }, zona = ZoneOffset.ofHours(-3))

    @Test
    fun `RF13 - mostra temperatura, 7 dias e a recomendacao do pivo`() {
        val clima = ClimaRepositoryMemoria(mapOf(fazendaId to comChuva))
        val estado = climaViewModel(clima).uiState.value

        assertTrue(estado.temPrevisao)
        assertEquals("26°", estado.temperaturaAtual)
        assertEquals("Chuva prevista a partir das 18h", estado.resumo)
        assertEquals(85, estado.probabilidadeJanela)
        assertTrue(estado.vaiChover)
        assertEquals("Dá para adiar a irrigação do pivô", estado.recomendacaoTitulo)
        assertEquals(7, estado.dias.size)
        assertEquals("Hoje", estado.dias[0].rotulo)
        assertEquals("Amanhã", estado.dias[1].rotulo)
        assertEquals("31°", estado.dias[0].maxima)
        assertEquals(CondicaoTempo.CHUVA, estado.dias[0].condicao)
        assertEquals("Atualizada às 14:40", estado.atualizadaEm)
        // AGORA = 14:40: a janela de 12 h vai das 14h às 2h do dia seguinte.
        assertEquals("até as 2h de amanhã", estado.janelaAte)
    }

    @Test
    fun `sem chuva a recomendacao e manter a irrigacao`() {
        val clima = ClimaRepositoryMemoria(mapOf(fazendaId to previsao()))
        val estado = climaViewModel(clima).uiState.value

        assertFalse(estado.vaiChover)
        assertEquals("Sem chuva relevante à vista", estado.recomendacaoTitulo)
        assertEquals("Nublado", estado.resumo)
    }

    @Test
    fun `limites do produtor mudam a recomendacao na hora`() {
        val clima = ClimaRepositoryMemoria(mapOf(fazendaId to comChuva))
        val ajustes = AjustesAvisoMemoria()
        val vm = climaViewModel(clima, ajustes)
        assertTrue(vm.uiState.value.vaiChover)

        ajustes.salvar(AjustesAviso(limites = LimitesChuva(probabilidadeMinima = 90, chuvaMinimaMm = 10.0)))

        assertFalse(vm.uiState.value.vaiChover)
    }

    @Test
    fun `abrir a tela busca previsao quando nao ha nenhuma guardada`() {
        val clima = ClimaRepositoryMemoria().apply { respostas = mapOf(fazendaId to comChuva) }
        val vm = climaViewModel(clima)

        assertEquals(1, clima.atualizacoes)
        assertTrue(vm.uiState.value.temPrevisao)
        assertFalse(vm.uiState.value.atualizando)
    }

    @Test
    fun `sem internet avisa e continua mostrando a guardada`() {
        val clima = ClimaRepositoryMemoria(mapOf(fazendaId to comChuva)).apply { semInternet = true }
        val vm = climaViewModel(clima)

        vm.aoAtualizar()

        assertEquals(ClimaViewModel.MENSAGEM_SEM_INTERNET, vm.uiState.value.mensagem)
        assertTrue(vm.uiState.value.temPrevisao)
    }

    @Test
    fun `previsao salva de outro dia diz o dia em que foi atualizada`() {
        fun atualizadaEm(obtidaEm: Instant) =
            climaViewModel(ClimaRepositoryMemoria(mapOf(fazendaId to comChuva.copy(obtidaEm = obtidaEm))))
                .uiState.value.atualizadaEm

        assertEquals("Atualizada às 14:40", atualizadaEm(AGORA))
        assertEquals("Atualizada ontem às 14:40", atualizadaEm(AGORA.minus(Duration.ofDays(1))))
        assertEquals("Atualizada em 29/09 às 14:40", atualizadaEm(AGORA.minus(Duration.ofDays(2))))
    }

    @Test
    fun `sem internet e sem previsao salva nao promete mostrar a salva`() {
        val vm = climaViewModel(ClimaRepositoryMemoria().apply { semInternet = true })

        assertFalse(vm.uiState.value.temPrevisao)
        assertEquals(ClimaViewModel.MENSAGEM_SEM_INTERNET_SEM_PREVISAO, vm.uiState.value.mensagem)
    }

    @Test
    fun `servico fora do ar pede para tentar de novo`() {
        val clima = ClimaRepositoryMemoria(mapOf(fazendaId to comChuva)).apply { indisponivel = true }
        val vm = climaViewModel(clima)

        vm.aoAtualizar()

        assertEquals(ClimaViewModel.MENSAGEM_FALHA, vm.uiState.value.mensagem)
        assertTrue(vm.uiState.value.temPrevisao)
    }

    @Test
    fun `trocar de propriedade mostra a previsao dela`() {
        val clima = ClimaRepositoryMemoria(
            mapOf(fazendaId to comChuva, DadosDeExemplo.ID_SITIO to previsao())
        )
        val vm = climaViewModel(clima)
        assertTrue(vm.uiState.value.vaiChover)

        vm.aoSelecionarPropriedade(DadosDeExemplo.ID_SITIO)

        assertEquals("Sítio Boa Vista", vm.uiState.value.nomePropriedade)
        assertFalse(vm.uiState.value.vaiChover)
    }

    @Test
    fun `sem propriedade a tela pede para cadastrar`() {
        val vm = climaViewModel(ClimaRepositoryMemoria(), propriedades = PropriedadeRepositoryMemoria(emptyList()))
        assertFalse(vm.uiState.value.carregando)
        assertFalse(vm.uiState.value.temPropriedade)
    }

    @Test
    fun `voltar para a tela do clima refaz a previsao com a hora nova`() {
        var agora = AGORA // 14:40 local do dia 1.
        val clima = ClimaRepositoryMemoria(mapOf(fazendaId to previsao(mapOf(25 to (90 to 4.0)))))
        val vm = ClimaViewModel(
            PropriedadeRepositoryMemoria(), clima, AjustesAvisoMemoria(),
            relogio = { agora.toEpochMilli() }, zona = ZoneOffset.ofHours(-3)
        )
        assertEquals("Chuva prevista a partir da 1h de amanhã", vm.uiState.value.resumo)
        assertEquals(7, vm.uiState.value.dias.size)

        vm.aoVoltarParaATela() // A primeira chamada corresponde à abertura da tela.
        agora = Instant.parse("2026-10-02T04:20:00Z") // 01:20 local do dia 2.
        assertEquals("sem voltar para a tela, nada muda", "Chuva prevista a partir da 1h de amanhã", vm.uiState.value.resumo)

        vm.aoVoltarParaATela()

        assertEquals("Chuva prevista já nesta hora", vm.uiState.value.resumo)
        assertEquals("o dia 1 saiu da lista", 6, vm.uiState.value.dias.size)
        assertEquals("Hoje", vm.uiState.value.dias[0].rotulo)
    }

    @Test
    fun `voltar para a tela do clima baixa a previsao so se precisar`() {
        val semPrevisao = ClimaRepositoryMemoria().apply { semInternet = true }
        val vm = climaViewModel(semPrevisao)
        val naAbertura = semPrevisao.atualizacoes

        vm.aoVoltarParaATela()
        assertEquals("a abertura já buscou: não busca duas vezes", naAbertura, semPrevisao.atualizacoes)

        vm.aoVoltarParaATela()
        assertEquals(naAbertura + 1, semPrevisao.atualizacoes)

        val emDia = ClimaRepositoryMemoria(mapOf(fazendaId to comChuva))
        val comCache = climaViewModel(emDia)
        comCache.aoVoltarParaATela()
        comCache.aoVoltarParaATela()
        assertEquals("cache em dia não é baixado de novo", 0, emDia.atualizacoes)
    }

    @Test
    fun `ajustes so gravam ao salvar e religam o agendamento`() {
        val ajustes = AjustesAvisoMemoria()
        val agenda = AgendaFalsa()
        val vm = AjustesAvisoViewModel(ajustes, agenda, PropriedadeRepositoryMemoria())

        vm.aoMudarProbabilidade(72f) // Arredonda para 70.
        vm.aoMudarChuva(3.3f) // Arredonda para 3,5.
        assertEquals(60, ajustes.ajustes.value.limites.probabilidadeMinima)

        vm.aoSalvar()

        assertEquals(70, ajustes.ajustes.value.limites.probabilidadeMinima)
        assertEquals(3.5, ajustes.ajustes.value.limites.chuvaMinimaMm, 0.0)
        assertEquals(true, agenda.ligada)
        assertTrue(vm.uiState.value.salvo)
    }

    @Test
    fun `desligar o aviso cancela a verificacao em segundo plano`() {
        val ajustes = AjustesAvisoMemoria()
        val agenda = AgendaFalsa()
        val vm = AjustesAvisoViewModel(ajustes, agenda, PropriedadeRepositoryMemoria())

        vm.aoMudarAtivo(false)
        vm.aoSalvar()

        assertFalse(ajustes.ajustes.value.ativo)
        assertEquals(false, agenda.ligada)
    }

    @Test
    fun `ferramenta de teste dispara a verificacao na hora`() {
        val agenda = AgendaFalsa()
        AjustesAvisoViewModel(AjustesAvisoMemoria(), agenda, PropriedadeRepositoryMemoria()).aoVerificarAgora()
        assertEquals(1, agenda.verificacoesManuais)
    }

    @Test
    fun `controles nao saem da faixa permitida`() {
        val vm = AjustesAvisoViewModel(AjustesAvisoMemoria(), AgendaFalsa(), PropriedadeRepositoryMemoria())
        vm.aoMudarProbabilidade(5f)
        vm.aoMudarChuva(99f)
        assertEquals(AjustesAvisoViewModel.PROBABILIDADE_MIN, vm.uiState.value.probabilidadeMinima)
        assertEquals(AjustesAvisoViewModel.CHUVA_MAX, vm.uiState.value.chuvaMinimaMm, 0.0)
    }

    @Test
    fun `cada sitio pode ficar sem aviso, e o desligado e gravado`() {
        val ajustes = AjustesAvisoMemoria()
        val vm = AjustesAvisoViewModel(ajustes, AgendaFalsa(), PropriedadeRepositoryMemoria())

        assertTrue("dois sítios: a lista aparece", vm.uiState.value.mostrarSitios)
        assertTrue("sítio novo já nasce avisando", vm.uiState.value.sitios.all { it.avisa })

        vm.aoMudarSitio(DadosDeExemplo.ID_SITIO, false)
        assertTrue("só grava ao salvar", ajustes.ajustes.value.propriedadesSemAviso.isEmpty())
        vm.aoSalvar()

        assertEquals(setOf(DadosDeExemplo.ID_SITIO), ajustes.ajustes.value.propriedadesSemAviso)
        assertFalse(ajustes.ajustes.value.avisaSobre(DadosDeExemplo.ID_SITIO))
        assertTrue(ajustes.ajustes.value.avisaSobre(DadosDeExemplo.ID_FAZENDA))
    }

    @Test
    fun `ajuste de um sitio fora da lista nao se perde ao salvar`() {
        val ajustes = AjustesAvisoMemoria(AjustesAviso(propriedadesSemAviso = setOf("sitio-de-outra-conta")))
        val vm = AjustesAvisoViewModel(ajustes, AgendaFalsa(), PropriedadeRepositoryMemoria())

        vm.aoMudarSitio(DadosDeExemplo.ID_FAZENDA, false)
        vm.aoSalvar()

        assertEquals(setOf("sitio-de-outra-conta", DadosDeExemplo.ID_FAZENDA), ajustes.ajustes.value.propriedadesSemAviso)
    }

    @Test
    fun `religar um sitio tira ele da lista de desligados`() {
        val ajustes = AjustesAvisoMemoria(AjustesAviso(propriedadesSemAviso = setOf(DadosDeExemplo.ID_SITIO)))
        val vm = AjustesAvisoViewModel(ajustes, AgendaFalsa(), PropriedadeRepositoryMemoria())
        assertFalse(vm.uiState.value.sitios.first { it.id == DadosDeExemplo.ID_SITIO }.avisa)

        vm.aoMudarSitio(DadosDeExemplo.ID_SITIO, true)
        vm.aoSalvar()

        assertTrue(ajustes.ajustes.value.propriedadesSemAviso.isEmpty())
    }

    @Test
    fun `com um sitio so a lista de sitios nao aparece`() {
        val um = PropriedadeRepositoryMemoria(DadosDeExemplo.propriedades().take(1))
        assertFalse(AjustesAvisoViewModel(AjustesAvisoMemoria(), AgendaFalsa(), um).uiState.value.mostrarSitios)
    }

    @Test
    fun `sitio que sobrou desligado continua com a lista, para dar para religar`() {
        val um = PropriedadeRepositoryMemoria(DadosDeExemplo.propriedades().take(1))
        val ajustes = AjustesAvisoMemoria(AjustesAviso(propriedadesSemAviso = setOf(DadosDeExemplo.ID_FAZENDA)))
        assertTrue(AjustesAvisoViewModel(ajustes, AgendaFalsa(), um).uiState.value.mostrarSitios)
    }

    private fun inicio(
        clima: ClimaRepositoryMemoria,
        agora: Instant = AGORA,
        nome: String? = null
    ) = InicioViewModel(
        LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(LocalDate.of(2026, 9, 30))),
        PropriedadeRepositoryMemoria(),
        clima,
        AjustesAvisoMemoria(),
        relogio = { agora.toEpochMilli() },
        nomeDoUsuario = flowOf(nome)
    )

    private fun cartaoCom(chuvaPorHora: Map<Int, Pair<Int, Double>>) =
        inicio(ClimaRepositoryMemoria(mapOf(fazendaId to previsao(chuvaPorHora)))).uiState.value.chuva

    @Test
    fun `inicio mostra a chuva prevista no cartao azul`() {
        val cartao = inicio(ClimaRepositoryMemoria(mapOf(fazendaId to comChuva))).uiState.value.chuva

        assertTrue(cartao?.vaiChover == true)
        assertEquals("Previsão · próximas 12 h", cartao?.rotulo)
        assertEquals("Chuva às 18h. Dá para adiar o pivô.", cartao?.frase)
        assertEquals("85% de chance · 6 mm", cartao?.detalhe)
    }

    @Test
    fun `chuva na hora de agora nao diz uma hora que ja comecou`() {
        // AGORA = 14:40; a chuva está na hora das 14h, já em andamento.
        assertEquals("Chuva já nesta hora. Dá para adiar o pivô.", cartaoCom(mapOf(14 to (90 to 4.0)))?.frase)
    }

    @Test
    fun `chuva depois da meia-noite avisa que e amanha`() {
        // Hora 25 = 1h do dia 2, ainda dentro da janela de 12 h.
        assertEquals("Chuva amanhã à 1h. Dá para adiar o pivô.", cartaoCom(mapOf(25 to (90 to 4.0)))?.frase)
    }

    @Test
    fun `sem chuva o cartao diz isso com a maior chance da janela`() {
        val cartao = cartaoCom(mapOf(20 to (30 to 0.0)))
        assertFalse(cartao?.vaiChover == true)
        assertEquals("Sem chuva nas próximas 12 h.", cartao?.frase)
        assertEquals("Até 30% de chance · toque para ver a semana", cartao?.detalhe)
    }

    @Test
    fun `chance alta abaixo do limite nao diz sem chuva`() {
        // 55% fica abaixo do limite padrão (60%), mas "Sem chuva" ao lado de
        // "55% de chance" seria contraditório.
        val cartao = cartaoCom(mapOf(20 to (55 to 0.0)))
        assertFalse(cartao?.vaiChover == true)
        assertEquals("Pode chover, mas abaixo do seu limite.", cartao?.frase)
        assertEquals("Até 55% de chance · seu limite: 60% ou 2 mm", cartao?.detalhe)
    }

    @Test
    fun `aviso so pela chance nao mostra 0 mm`() {
        val cartao = cartaoCom(mapOf(18 to (85 to 0.0)))
        assertTrue(cartao?.vaiChover == true)
        assertEquals("85% de chance", cartao?.detalhe)
    }

    @Test
    fun `previsao que nao cobre as proximas horas pede para atualizar`() {
        val tresDiasDepois = AGORA.plus(Duration.ofDays(3))
        val cartao = inicio(ClimaRepositoryMemoria(mapOf(fazendaId to comChuva)), agora = tresDiasDepois).uiState.value.chuva
        assertEquals("Previsão desatualizada.", cartao?.frase)
        assertEquals("Toque para atualizar", cartao?.detalhe)
        assertFalse(cartao?.vaiChover == true)
    }

    @Test
    fun `botao da conta mostra as iniciais de quem esta conectado`() {
        assertEquals("MS", inicio(ClimaRepositoryMemoria(), nome = "Maria da Silva Souza").uiState.value.iniciais)
        assertEquals("", inicio(ClimaRepositoryMemoria(), nome = null).uiState.value.iniciais)
    }

    @Test
    fun `iniciais continuam quando o mes muda`() {
        val vm = inicio(ClimaRepositoryMemoria(), nome = "Maria Souza")
        vm.aoVoltarMes()
        assertEquals("MS", vm.uiState.value.iniciais)
    }

    @Test
    fun `inicio sem previsao guardada mostra o cartao padrao`() {
        assertNull(inicio(ClimaRepositoryMemoria()).uiState.value.chuva)
    }

    @Test
    fun `cartao de chuva nao some quando um lancamento novo chega`() {
        val vm = inicio(ClimaRepositoryMemoria(mapOf(fazendaId to comChuva)))
        vm.aoVoltarMes()
        assertTrue(vm.uiState.value.chuva?.vaiChover == true)
    }

    @Test
    fun `voltar para a tela refaz a frase da chuva com a hora nova`() {
        var agora = AGORA // 14:40 local do dia 1.
        val vm = InicioViewModel(
            LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(LocalDate.of(2026, 9, 30))),
            PropriedadeRepositoryMemoria(),
            ClimaRepositoryMemoria(mapOf(fazendaId to previsao(mapOf(25 to (90 to 4.0))))),
            AjustesAvisoMemoria(),
            relogio = { agora.toEpochMilli() }
        )
        assertEquals("Chuva amanhã à 1h. Dá para adiar o pivô.", vm.uiState.value.chuva?.frase)

        vm.aoVoltarParaATela() // A primeira chamada corresponde à abertura da tela.
        agora = Instant.parse("2026-10-02T04:20:00Z") // 01:20 local do dia 2.
        assertEquals("sem voltar para a tela, nada muda", "Chuva amanhã à 1h. Dá para adiar o pivô.", vm.uiState.value.chuva?.frase)

        vm.aoVoltarParaATela()
        assertEquals("Chuva já nesta hora. Dá para adiar o pivô.", vm.uiState.value.chuva?.frase)
    }

    @Test
    fun `voltar para a tela confere se a previsao precisa ser baixada de novo`() {
        val clima = ClimaRepositoryMemoria().apply { semInternet = true }
        val vm = inicio(clima)
        val naAbertura = clima.atualizacoes

        vm.aoVoltarParaATela()
        assertEquals("a abertura já buscou: não busca duas vezes", naAbertura, clima.atualizacoes)

        vm.aoVoltarParaATela()
        assertEquals(naAbertura + 1, clima.atualizacoes)
    }
}
