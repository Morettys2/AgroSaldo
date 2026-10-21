package br.joao.agrosaldo.feature

import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.relogioEm
import br.joao.agrosaldo.data.repository.LancamentoRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.feature.historico.HistoricoViewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Histórico do ano.
 *
 * Com os dados de exemplo e "hoje" em 30/09/2026, a Fazenda Santa Clara tem:
 *   agosto:   entrou R$ 9.400  · saiu R$ 2.100  · saldo R$  7.300
 *   setembro: entrou R$ 18.000 · saiu R$ 2.350  · saldo R$ 15.650
 */
@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle e runCurrent
class HistoricoViewModelTest {

    /** Propriedade e mês escolhidos; cada teste tem a sua, sem estado global. */
    private val selecao = SelecaoAtual(relogio = relogioEm(LocalDate.of(2026, 9, 30)))

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hoje = LocalDate.of(2026, 9, 30)
    private lateinit var lancamentos: LancamentoRepositoryMemoria

    @Before
    fun preparar() {
        selecao.limpar()
        selecao.selecionar(DadosDeExemplo.ID_FAZENDA)
        selecao.selecionarMes(YearMonth.of(2026, 9))
        lancamentos = LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(hoje))
    }

    private fun viewModel() = HistoricoViewModel(lancamentos, PropriedadeRepositoryMemoria(), relogio = relogioEm(hoje), selecao = selecao)

    @Test
    fun `RF10 - mostra os 12 meses do ano com entradas e saidas de cada um`() {
        val estado = viewModel().uiState.value

        assertEquals(2026, estado.ano)
        assertEquals(12, estado.meses.size)
        assertEquals(YearMonth.of(2026, 1), estado.meses.first().mes)

        val agosto = estado.meses[7]
        assertEquals(940_000L, agosto.receitasCentavos)
        assertEquals(210_000L, agosto.despesasCentavos)

        val setembro = estado.meses[8]
        assertEquals(1_800_000L, setembro.receitasCentavos)
        assertEquals(235_000L, setembro.despesasCentavos)
        assertEquals(1_565_000L, setembro.saldoCentavos)

        assertFalse(estado.meses[0].temMovimento)
    }

    @Test
    fun `totais do ano e a escala do grafico`() {
        val estado = viewModel().uiState.value

        assertEquals(2_740_000L, estado.receitasCentavos)
        assertEquals(445_000L, estado.despesasCentavos)
        assertEquals(2_295_000L, estado.saldoCentavos)
        assertEquals("a barra mais alta é o maior valor de um mês", 1_800_000L, estado.maiorValorMensal)
    }

    @Test
    fun `a escala do grafico considera um mes em que saiu mais do que entrou`() = runTest {
        lancamentos.salvar(
            Lancamento(
                propriedadeId = DadosDeExemplo.ID_FAZENDA,
                tipo = TipoLancamento.DESPESA,
                categoria = CategoriaLancamento.SEMENTES,
                valorCentavos = 2_500_000L,
                data = LocalDate.of(2026, 2, 10)
            )
        )
        assertEquals(2_500_000L, viewModel().uiState.value.maiorValorMensal)
    }

    @Test
    fun `lancamento de outro ano nao entra no ano escolhido`() = runTest {
        lancamentos.salvar(
            Lancamento(
                propriedadeId = DadosDeExemplo.ID_FAZENDA,
                tipo = TipoLancamento.DESPESA,
                categoria = CategoriaLancamento.DEFENSIVOS,
                valorCentavos = 900_000L,
                data = LocalDate.of(2025, 9, 10)
            )
        )
        val vm = viewModel()
        assertEquals(445_000L, vm.uiState.value.despesasCentavos)
        assertTrue(
            "nem nos maiores gastos do ano",
            vm.uiState.value.gastosDoAno.none { it.categoria == CategoriaLancamento.DEFENSIVOS }
        )

        vm.aoVoltarAno()
        assertEquals(900_000L, vm.uiState.value.despesasCentavos)
        assertEquals(CategoriaLancamento.DEFENSIVOS, vm.uiState.value.gastosDoAno.single().categoria)
    }

    @Test
    fun `melhor mes e mes mais fraco`() {
        val estado = viewModel().uiState.value
        assertEquals(YearMonth.of(2026, 9), estado.melhorMes?.mes)
        assertEquals(YearMonth.of(2026, 8), estado.piorMes?.mes)
    }

    @Test
    fun `mes futuro com lancamento agendado nao vira melhor nem mais fraco`() = runTest {
        lancamentos.salvar(
            Lancamento(
                propriedadeId = DadosDeExemplo.ID_FAZENDA,
                tipo = TipoLancamento.RECEITA,
                categoria = CategoriaLancamento.ARRENDAMENTO,
                valorCentavos = 5_000_000L,
                data = LocalDate.of(2026, 11, 10)
            )
        )
        lancamentos.salvar(
            Lancamento(
                propriedadeId = DadosDeExemplo.ID_FAZENDA,
                tipo = TipoLancamento.DESPESA,
                categoria = CategoriaLancamento.SEMENTES,
                valorCentavos = 9_000_000L,
                data = LocalDate.of(2026, 12, 10)
            )
        )
        val estado = viewModel().uiState.value

        assertEquals(YearMonth.of(2026, 9), estado.melhorMes?.mes)
        assertEquals(YearMonth.of(2026, 8), estado.piorMes?.mes)
    }

    @Test
    fun `com um mes so de movimento nao existe mes mais fraco`() {
        lancamentos = LancamentoRepositoryMemoria(
            listOf(
                Lancamento(
                    propriedadeId = DadosDeExemplo.ID_FAZENDA,
                    tipo = TipoLancamento.DESPESA,
                    categoria = CategoriaLancamento.ENERGIA,
                    valorCentavos = 10_000L,
                    data = hoje
                )
            )
        )
        val estado = viewModel().uiState.value
        assertEquals(YearMonth.of(2026, 9), estado.melhorMes?.mes)
        assertNull(estado.piorMes)
    }

    @Test
    fun `dois meses empatados nao viram melhor e mais fraco ao mesmo tempo`() {
        lancamentos = LancamentoRepositoryMemoria(
            listOf(8, 9).map { mes ->
                Lancamento(
                    propriedadeId = DadosDeExemplo.ID_FAZENDA,
                    tipo = TipoLancamento.DESPESA,
                    categoria = CategoriaLancamento.ENERGIA,
                    valorCentavos = 10_000L,
                    data = LocalDate.of(2026, mes, 5)
                )
            }
        )
        val estado = viewModel().uiState.value
        assertEquals(YearMonth.of(2026, 8), estado.melhorMes?.mes)
        assertNull(estado.piorMes)
    }

    @Test
    fun `maiores gastos do ano somam todos os meses e vem do maior para o menor`() {
        val gastos = viewModel().uiState.value.gastosDoAno

        assertEquals(
            listOf(
                CategoriaLancamento.MAO_DE_OBRA,
                CategoriaLancamento.ENERGIA,
                CategoriaLancamento.ADUBO,
                CategoriaLancamento.COMBUSTIVEL
            ),
            gastos.map { it.categoria }
        )
        assertEquals(1f, gastos.first().proporcao, 0.0001f)
        assertEquals(125_000f / 210_000f, gastos[1].proporcao, 0.0001f)
    }

    @Test
    fun `meses depois de hoje aparecem como futuros`() {
        val meses = viewModel().uiState.value.meses
        assertFalse(meses[8].futuro)
        assertTrue(meses[9].futuro)
        assertTrue(meses[11].futuro)
    }

    @Test
    fun `lista mes a mes omite mes futuro sem lancamento e mantem o futuro agendado`() = runTest {
        lancamentos.salvar(
            Lancamento(
                propriedadeId = DadosDeExemplo.ID_FAZENDA,
                tipo = TipoLancamento.RECEITA,
                categoria = CategoriaLancamento.ARRENDAMENTO,
                valorCentavos = 100_000L,
                data = LocalDate.of(2026, 11, 10)
            )
        )

        val naLista = viewModel().uiState.value.mesesNaLista.map { it.mes.monthValue }

        // Janeiro a setembro (passados, mesmo vazios) e novembro (futuro com lançamento).
        assertEquals((1..9).toList() + 11, naLista)
    }

    @Test
    fun `lista mes a mes sem agendamentos termina no mes atual`() {
        val naLista = viewModel().uiState.value.mesesNaLista

        assertEquals((1..9).toList(), naLista.map { it.mes.monthValue })
        assertTrue(naLista.none { it.futuro })
    }

    @Test
    fun `cada propriedade tem o seu historico`() {
        val vm = viewModel()
        vm.aoSelecionarPropriedade(DadosDeExemplo.ID_SITIO)

        val estado = vm.uiState.value
        assertEquals("Sítio Boa Vista", estado.nomePropriedade)
        assertEquals(450_000L, estado.receitasCentavos)
        assertEquals(273_000L, estado.despesasCentavos)
    }

    @Test
    fun `ano anterior sem lancamentos fica vazio`() {
        val vm = viewModel()
        vm.aoVoltarAno()

        assertEquals(2025, vm.uiState.value.ano)
        assertFalse(vm.uiState.value.temMovimento)
        assertTrue(vm.uiState.value.podeAvancar)
    }

    @Test
    fun `avanca no maximo ate o ano que vem`() {
        val vm = viewModel()
        vm.aoAvancarAno()
        assertEquals(2027, vm.uiState.value.ano)
        assertFalse(vm.uiState.value.podeAvancar)

        vm.aoAvancarAno()
        assertEquals(2027, vm.uiState.value.ano)
    }

    @Test
    fun `abre no ano do mes que estava escolhido`() {
        selecao.selecionarMes(YearMonth.of(2025, 3))
        assertEquals(2025, viewModel().uiState.value.ano)
    }

    @Test
    fun `tocar num mes deixa ele escolhido para a aba Lancamentos`() {
        viewModel().aoAbrirMes(YearMonth.of(2026, 8))
        assertEquals(YearMonth.of(2026, 8), selecao.mes.value)
    }

    @Test
    fun `lancamento novo entra no grafico na hora`() = runTest {
        val vm = viewModel()
        lancamentos.salvar(
            Lancamento(
                propriedadeId = DadosDeExemplo.ID_FAZENDA,
                tipo = TipoLancamento.RECEITA,
                categoria = CategoriaLancamento.ARRENDAMENTO,
                valorCentavos = 100_000L,
                data = LocalDate.of(2026, 1, 10)
            )
        )
        assertEquals(100_000L, vm.uiState.value.meses[0].receitasCentavos)
    }

    @Test
    fun `erro ao ler os lancamentos vira mensagem e o grafico volta sozinho`() = runTest {
        var leituras = 0
        val instavel = object : LancamentoRepository by lancamentos {
            override fun observarTodos(): Flow<List<Lancamento>> = flow {
                if (leituras++ == 0) throw IllegalStateException("SQLiteDatabaseLockedException: database is locked")
                emitAll(lancamentos.observarTodos())
            }
        }
        val vm = HistoricoViewModel(instavel, PropriedadeRepositoryMemoria(), relogio = relogioEm(hoje), selecao = selecao)

        assertEquals(HistoricoViewModel.MENSAGEM_ERRO, vm.uiState.value.erro)

        advanceUntilIdle()
        assertNull(vm.uiState.value.erro)
        assertTrue(vm.uiState.value.temMovimento)
    }

    @Test
    fun `sem propriedade nao quebra`() {
        val vm =
            HistoricoViewModel(
                LancamentoRepositoryMemoria(emptyList()),
                PropriedadeRepositoryMemoria(emptyList()),
                relogio = relogioEm(hoje),
                selecao = selecao
            )
        assertFalse(vm.uiState.value.carregando)
        assertFalse(vm.uiState.value.temPropriedade)
    }
}
