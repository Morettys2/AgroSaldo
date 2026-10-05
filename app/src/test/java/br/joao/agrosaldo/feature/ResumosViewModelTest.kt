package br.joao.agrosaldo.feature

import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.preferencias.AjustesAvisoMemoria
import br.joao.agrosaldo.data.repository.ClimaRepositoryMemoria
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
        // Mês e propriedade são compartilhados entre as abas: cada teste começa do zero.
        SelecaoAtual.limpar()
        SelecaoAtual.selecionar(DadosDeExemplo.ID_FAZENDA)
    }

    // ---------- Início ----------

    @Test
    fun `RF08 - inicio mostra entradas, saidas e saldo do mes`() {
        val estado = InicioViewModel(lancamentos, propriedades, ClimaRepositoryMemoria(), AjustesAvisoMemoria()).uiState.value

        assertFalse(estado.carregando)
        assertEquals("Fazenda São João da Serra", estado.nomePropriedade)
        assertEquals(1_800_000L, estado.receitasCentavos)
        assertEquals(235_000L, estado.despesasCentavos)
        assertEquals(1_565_000L, estado.saldoCentavos)
    }

    @Test
    fun `pra onde foi o dinheiro ordena do maior gasto para o menor`() {
        val gastos = InicioViewModel(lancamentos, propriedades, ClimaRepositoryMemoria(), AjustesAvisoMemoria()).uiState.value.gastosPorCategoria

        assertEquals(
            listOf(CategoriaLancamento.ENERGIA, CategoriaLancamento.ADUBO, CategoriaLancamento.COMBUSTIVEL),
            gastos.map { it.categoria }
        )
        assertEquals("a maior barra sempre enche", 1f, gastos.first().proporcao, 0.0001f)
        assertEquals(78_000f / 125_000f, gastos[1].proporcao, 0.0001f)
    }

    @Test
    fun `RF10 - voltar um mes mostra o historico do mes anterior`() {
        val vm = InicioViewModel(lancamentos, propriedades, ClimaRepositoryMemoria(), AjustesAvisoMemoria())
        vm.aoVoltarMes()

        val estado = vm.uiState.value
        assertEquals(YearMonth.now().minusMonths(1), estado.mes)
        assertEquals(940_000L, estado.receitasCentavos)
        assertEquals(210_000L, estado.despesasCentavos)
    }

    @Test
    fun `trocar de propriedade recalcula tudo`() {
        val vm = InicioViewModel(lancamentos, propriedades, ClimaRepositoryMemoria(), AjustesAvisoMemoria())
        vm.aoSelecionarPropriedade(DadosDeExemplo.ID_SITIO)

        val estado = vm.uiState.value
        assertEquals("Sítio Boa Vista", estado.nomePropriedade)
        assertEquals(450_000L - 210_000L, estado.saldoCentavos)
    }

    @Test
    fun `lancamento novo aparece no inicio sem ninguem pedir`() = runTest {
        val vm = InicioViewModel(lancamentos, propriedades, ClimaRepositoryMemoria(), AjustesAvisoMemoria())
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
        val vm = InicioViewModel(lancamentos, propriedades, ClimaRepositoryMemoria(), AjustesAvisoMemoria())
        repeat(3) { vm.aoAvancarMes() }

        assertEquals(0L, vm.uiState.value.saldoCentavos)
        assertEquals(0.5f, vm.uiState.value.proporcaoReceitas, 0.0001f)
    }

    @Test
    fun `sem nenhuma propriedade o inicio pede para cadastrar`() {
        val vm = InicioViewModel(LancamentoRepositoryMemoria(emptyList()), PropriedadeRepositoryMemoria(emptyList()), ClimaRepositoryMemoria(), AjustesAvisoMemoria())
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

    // ---------- filtro por categoria (RF09) ----------

    @Test
    fun `RF09 - filtrar por categoria mostra so ela e soma o que aparece`() {
        val vm = LancamentoListaViewModel(lancamentos, propriedades)
        vm.aoMudarCategoria(CategoriaLancamento.ENERGIA)

        val estado = vm.uiState.value
        assertEquals(1, estado.quantidade)
        assertTrue(estado.lancamentos.all { it.categoria == CategoriaLancamento.ENERGIA })
        assertEquals("quanto saiu de energia no mês", -125_000L, estado.totalFiltradoCentavos)
        assertEquals("o saldo continua o do mês", 1_565_000L, estado.saldoCentavos)
        assertTrue(estado.temFiltro)
    }

    @Test
    fun `chips mostram so as categorias que tem lancamento no mes, na ordem do cadastro`() {
        val categorias = LancamentoListaViewModel(lancamentos, propriedades).uiState.value.categoriasDoMes
        assertEquals(
            listOf(
                CategoriaLancamento.ENERGIA, CategoriaLancamento.ADUBO,
                CategoriaLancamento.COMBUSTIVEL, CategoriaLancamento.VENDA_SAFRA
            ),
            categorias
        )
    }

    @Test
    fun `com filtro de despesas os chips nao oferecem categorias de receita`() {
        val vm = LancamentoListaViewModel(lancamentos, propriedades)
        vm.aoMudarFiltro(FiltroLancamento.DESPESAS)
        assertTrue(vm.uiState.value.categoriasDoMes.none { it.tipo == TipoLancamento.RECEITA })
    }

    @Test
    fun `trocar para receitas desmarca uma categoria de despesa`() {
        val vm = LancamentoListaViewModel(lancamentos, propriedades)
        vm.aoMudarCategoria(CategoriaLancamento.ADUBO)
        vm.aoMudarFiltro(FiltroLancamento.RECEITAS)

        assertEquals(null, vm.uiState.value.categoria)
        assertEquals(1, vm.uiState.value.quantidade)
    }

    @Test
    fun `trocar para despesas desmarca uma categoria de receita`() {
        val vm = LancamentoListaViewModel(lancamentos, propriedades)
        vm.aoMudarCategoria(CategoriaLancamento.VENDA_SAFRA)
        vm.aoMudarFiltro(FiltroLancamento.DESPESAS)

        assertEquals(null, vm.uiState.value.categoria)
        assertEquals(3, vm.uiState.value.quantidade)
    }

    @Test
    fun `trocar para despesas mantem uma categoria de despesa marcada`() {
        val vm = LancamentoListaViewModel(lancamentos, propriedades)
        vm.aoMudarCategoria(CategoriaLancamento.ADUBO)
        vm.aoMudarFiltro(FiltroLancamento.DESPESAS)
        assertEquals(CategoriaLancamento.ADUBO, vm.uiState.value.categoria)
    }

    @Test
    fun `tocar de novo na categoria marcada desmarca, e limpar tira tudo`() {
        val vm = LancamentoListaViewModel(lancamentos, propriedades)
        vm.aoMudarCategoria(CategoriaLancamento.ADUBO)
        vm.aoMudarCategoria(CategoriaLancamento.ADUBO)
        assertEquals(null, vm.uiState.value.categoria)

        vm.aoMudarFiltro(FiltroLancamento.DESPESAS)
        vm.aoMudarCategoria(CategoriaLancamento.ADUBO)
        vm.aoLimparFiltros()
        assertFalse(vm.uiState.value.temFiltro)
        assertEquals(4, vm.uiState.value.quantidade)
    }

    @Test
    fun `categoria marcada continua no chip mesmo num mes sem ela`() {
        val vm = LancamentoListaViewModel(lancamentos, propriedades)
        vm.aoMudarCategoria(CategoriaLancamento.ADUBO)
        vm.aoVoltarMes()

        val estado = vm.uiState.value
        assertEquals(0, estado.quantidade)
        assertTrue("senão não daria para desmarcar", CategoriaLancamento.ADUBO in estado.categoriasDoMes)
    }

    // ---------- mês compartilhado ----------

    @Test
    fun `o mes escolhido no inicio e o mesmo em lancamentos e sitios`() {
        val inicio = InicioViewModel(lancamentos, propriedades, ClimaRepositoryMemoria(), AjustesAvisoMemoria())
        val lista = LancamentoListaViewModel(lancamentos, propriedades)
        val sitios = PropriedadeListaViewModel(propriedades, lancamentos)

        inicio.aoVoltarMes()

        val mesPassado = YearMonth.now().minusMonths(1)
        assertEquals(mesPassado, lista.uiState.value.mes)
        assertEquals(mesPassado, sitios.uiState.value.mes)
        assertEquals(2, lista.uiState.value.quantidade)
    }
}
