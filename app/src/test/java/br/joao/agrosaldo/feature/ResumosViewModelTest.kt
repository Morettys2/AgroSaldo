package br.joao.agrosaldo.feature

import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.repository.LancamentoRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.feature.inicio.InicioViewModel
import br.joao.agrosaldo.feature.lancamento.FiltroLancamento
import br.joao.agrosaldo.feature.lancamento.LancamentoListaViewModel
import br.joao.agrosaldo.feature.propriedade.PropriedadeListaViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Testa os cálculos das telas de resumo: Início, Lançamentos e Sítios.
 *
 * Os números esperados são os mesmos que aparecem no emulador com os dados
 * de exemplo — dá para abrir o app e conferir lado a lado.
 *
 * Fazenda São João, mês corrente:
 *   entrou  R$ 18.000   (venda de milho)
 *   saiu    R$  2.350   (energia 1.250 + adubo 780 + combustível 320)
 *   saldo   R$ 15.650
 */
class ResumosViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var propriedades: PropriedadeRepositoryMemoria
    private lateinit var lancamentos: LancamentoRepositoryMemoria

    @Before
    fun preparar() {
        propriedades = PropriedadeRepositoryMemoria()
        // Relativo a hoje, porque as telas abrem sempre no mês corrente.
        lancamentos = LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(LocalDate.now()))
        SelecaoAtual.selecionar(DadosDeExemplo.ID_FAZENDA)
    }

    // ---------- Início ----------

    @Test
    fun `RF08 - inicio mostra entradas, saidas e saldo do mes`() {
        val estado = InicioViewModel(lancamentos, propriedades).uiState.value

        assertFalse(estado.carregando)
        assertEquals("Fazenda São João da Serra", estado.nomePropriedade)
        assertEquals(1_800_000L, estado.receitasCentavos)
        assertEquals(235_000L, estado.despesasCentavos)
        assertEquals(1_565_000L, estado.saldoCentavos)
    }

    @Test
    fun `pra onde foi o dinheiro ordena do maior gasto para o menor`() {
        val gastos = InicioViewModel(lancamentos, propriedades).uiState.value.gastosPorCategoria

        assertEquals(
            listOf(CategoriaLancamento.ENERGIA, CategoriaLancamento.ADUBO, CategoriaLancamento.COMBUSTIVEL),
            gastos.map { it.categoria }
        )
        assertEquals("a maior barra sempre enche", 1f, gastos.first().proporcao, 0.0001f)
        assertEquals(78_000f / 125_000f, gastos[1].proporcao, 0.0001f)
    }

    @Test
    fun `RF10 - voltar um mes mostra o historico do mes anterior`() {
        val vm = InicioViewModel(lancamentos, propriedades)
        vm.aoVoltarMes()

        val estado = vm.uiState.value
        assertEquals(YearMonth.now().minusMonths(1), estado.mes)
        assertEquals(940_000L, estado.receitasCentavos)
        assertEquals(210_000L, estado.despesasCentavos)
    }

    @Test
    fun `trocar de propriedade recalcula tudo`() {
        val vm = InicioViewModel(lancamentos, propriedades)
        vm.aoSelecionarPropriedade(DadosDeExemplo.ID_SITIO)

        val estado = vm.uiState.value
        assertEquals("Sítio Boa Vista", estado.nomePropriedade)
        assertEquals(450_000L - 210_000L, estado.saldoCentavos)
    }

    @Test
    fun `lancamento novo aparece no inicio sem ninguem pedir`() = runTest {
        val vm = InicioViewModel(lancamentos, propriedades)
        lancamentos.salvar(
            Lancamento(
                propriedadeId = DadosDeExemplo.ID_FAZENDA,
                tipo = TipoLancamento.RECEITA, categoria = CategoriaLancamento.ARRENDAMENTO,
                valorCentavos = 50_000L, data = LocalDate.now()
            )
        )

        assertEquals(1_615_000L, vm.uiState.value.saldoCentavos)
    }

    @Test
    fun `mes sem movimento deixa a barra meio a meio`() {
        val vm = InicioViewModel(lancamentos, propriedades)
        repeat(3) { vm.aoAvancarMes() }

        assertEquals(0L, vm.uiState.value.saldoCentavos)
        assertEquals(0.5f, vm.uiState.value.proporcaoReceitas, 0.0001f)
    }

    @Test
    fun `sem nenhuma propriedade o inicio pede para cadastrar`() {
        val vm = InicioViewModel(LancamentoRepositoryMemoria(emptyList()), PropriedadeRepositoryMemoria(emptyList()))
        assertFalse(vm.uiState.value.temPropriedade)
        assertFalse(vm.uiState.value.carregando)
    }

    // ---------- Lançamentos ----------

    @Test
    fun `RF09 - filtro de despesas esconde receitas mas o saldo continua o do mes`() {
        val vm = LancamentoListaViewModel(lancamentos, propriedades)
        vm.aoMudarFiltro(FiltroLancamento.DESPESAS)

        val estado = vm.uiState.value
        assertEquals(3, estado.quantidade)
        assertTrue(estado.lancamentos.all { it.tipo == TipoLancamento.DESPESA })
        assertEquals("filtrar não pode mudar o saldo", 1_565_000L, estado.saldoCentavos)
    }

    @Test
    fun `lista vem da data mais recente para a mais antiga`() {
        val datas = LancamentoListaViewModel(lancamentos, propriedades).uiState.value.lancamentos.map { it.data }
        assertEquals(datas.sortedDescending(), datas)
    }

    // ---------- Sítios ----------

    @Test
    fun `RF11 - comparativo mostra o saldo de cada propriedade e o total`() {
        val estado = PropriedadeListaViewModel(propriedades, lancamentos).uiState.value

        val porId = estado.itens.associateBy { it.propriedade.id }
        assertEquals(1_565_000L, porId.getValue(DadosDeExemplo.ID_FAZENDA).saldoCentavos)
        assertEquals(240_000L, porId.getValue(DadosDeExemplo.ID_SITIO).saldoCentavos)
        assertEquals(1_805_000L, estado.totalCentavos)
        assertEquals(1f, porId.getValue(DadosDeExemplo.ID_FAZENDA).proporcao, 0.0001f)
    }

    @Test
    fun `propriedade no prejuizo tem barra proporcional ao tamanho do prejuizo`() = runTest {
        lancamentos.salvar(
            Lancamento(
                propriedadeId = DadosDeExemplo.ID_SITIO,
                tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.MANUTENCAO,
                valorCentavos = 2_000_000L, data = LocalDate.now()
            )
        )
        val sitio = PropriedadeListaViewModel(propriedades, lancamentos).uiState.value
            .itens.first { it.propriedade.id == DadosDeExemplo.ID_SITIO }

        assertTrue(sitio.saldoCentavos < 0)
        assertEquals(1f, sitio.proporcao, 0.0001f)
    }
}
