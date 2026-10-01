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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Telas de clima (16), ajustes do aviso (18) e o cartão de chuva do Início. */
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

    // ---------- tela de clima ----------

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
        // 14:40 → a janela de 12 h começa às 14h e termina às 2h do dia seguinte.
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

    // ---------- ajustes do aviso ----------

    @Test
    fun `ajustes so gravam ao salvar e religam o agendamento`() {
        val ajustes = AjustesAvisoMemoria()
        val agenda = AgendaFalsa()
        val vm = AjustesAvisoViewModel(ajustes, agenda)

        vm.aoMudarProbabilidade(72f)    // vira 70
        vm.aoMudarChuva(3.3f)           // vira 3,5
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
        val vm = AjustesAvisoViewModel(ajustes, agenda)

        vm.aoMudarAtivo(false)
        vm.aoSalvar()

        assertFalse(ajustes.ajustes.value.ativo)
        assertEquals(false, agenda.ligada)
    }

    @Test
    fun `ferramenta de teste dispara a verificacao na hora`() {
        val agenda = AgendaFalsa()
        AjustesAvisoViewModel(AjustesAvisoMemoria(), agenda).aoVerificarAgora()
        assertEquals(1, agenda.verificacoesManuais)
    }

    @Test
    fun `controles nao saem da faixa permitida`() {
        val vm = AjustesAvisoViewModel(AjustesAvisoMemoria(), AgendaFalsa())
        vm.aoMudarProbabilidade(5f)
        vm.aoMudarChuva(99f)
        assertEquals(AjustesAvisoViewModel.PROBABILIDADE_MIN, vm.uiState.value.probabilidadeMinima)
        assertEquals(AjustesAvisoViewModel.CHUVA_MAX, vm.uiState.value.chuvaMinimaMm, 0.0)
    }

    // ---------- cartão do Início ----------

    private fun inicio(clima: ClimaRepositoryMemoria) = InicioViewModel(
        LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(LocalDate.of(2026, 9, 30))),
        PropriedadeRepositoryMemoria(),
        clima,
        AjustesAvisoMemoria(),
        relogio = { AGORA.toEpochMilli() }
    )

    @Test
    fun `inicio mostra a chuva prevista no cartao azul`() {
        val cartao = inicio(ClimaRepositoryMemoria(mapOf(fazendaId to comChuva))).uiState.value.chuva

        assertTrue(cartao?.vaiChover == true)
        assertEquals("Chuva prevista a partir das 18h", cartao?.titulo)
        assertEquals("85% de chance · 6 mm · dá para adiar o pivô", cartao?.detalhe)
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
}
