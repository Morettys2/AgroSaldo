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
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.feature.inicio.InicioViewModel
import br.joao.agrosaldo.feature.lancamento.FiltroLancamento
import br.joao.agrosaldo.feature.lancamento.LancamentoListaViewModel
import br.joao.agrosaldo.feature.propriedade.PropriedadeListaViewModel
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
import java.time.LocalDate
import java.time.YearMonth

/**
 * Cálculos das telas de resumo: Início, Lançamentos e Sítios.
 *
 * Com [DadosDeExemplo], a Fazenda São João no mês corrente tem:
 *   entrou  R$ 18.000   (venda de milho)
 *   saiu    R$  2.350   (energia 1.250 + adubo 780 + combustível 320)
 *   saldo   R$ 15.650
 */
class ResumosViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var propriedades: PropriedadeRepositoryMemoria
    private lateinit var lancamentos: LancamentoRepositoryMemoria

    /** Dia fixo: com o relógio real, os testes poderiam falhar na virada do mês. */
    private val hoje = LocalDate.of(2026, 9, 30)
    private val mesDeHoje = YearMonth.from(hoje)

    @Before
    fun preparar() {
        propriedades = PropriedadeRepositoryMemoria()
        // Datas relativas a [hoje]: as telas abrem no mês corrente.
        lancamentos = LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(hoje))
        // SelecaoAtual é global (compartilhada entre as abas); limpa para isolar os testes.
        SelecaoAtual.limpar()
        SelecaoAtual.selecionar(DadosDeExemplo.ID_FAZENDA)
        SelecaoAtual.selecionarMes(mesDeHoje)
    }

    private fun inicio() = InicioViewModel(lancamentos, propriedades, ClimaRepositoryMemoria(), AjustesAvisoMemoria(), hoje = { hoje })

    private fun lista() = LancamentoListaViewModel(lancamentos, propriedades, hoje = { hoje })

    @Test
    fun `RF08 - inicio mostra entradas, saidas e saldo do mes`() {
        val estado = inicio().uiState.value

        assertFalse(estado.carregando)
        assertEquals("Fazenda São João da Serra", estado.nomePropriedade)
        assertEquals(1_800_000L, estado.receitasCentavos)
        assertEquals(235_000L, estado.despesasCentavos)
        assertEquals(1_565_000L, estado.saldoCentavos)
    }

    @Test
    fun `pra onde foi o dinheiro ordena do maior gasto para o menor`() {
        val gastos = inicio().uiState.value.gastosPorCategoria

        assertEquals(
            listOf(CategoriaLancamento.ENERGIA, CategoriaLancamento.ADUBO, CategoriaLancamento.COMBUSTIVEL),
            gastos.map { it.categoria }
        )
        assertEquals("a maior barra sempre enche", 1f, gastos.first().proporcao, 0.0001f)
        assertEquals(78_000f / 125_000f, gastos[1].proporcao, 0.0001f)
    }

    @Test
    fun `RF10 - voltar um mes mostra o historico do mes anterior`() {
        val vm = inicio()
        vm.aoVoltarMes()

        val estado = vm.uiState.value
        assertEquals(mesDeHoje.minusMonths(1), estado.mes)
        assertEquals(940_000L, estado.receitasCentavos)
        assertEquals(210_000L, estado.despesasCentavos)
    }

    @Test
    fun `trocar de propriedade recalcula tudo`() {
        val vm = inicio()
        vm.aoSelecionarPropriedade(DadosDeExemplo.ID_SITIO)

        val estado = vm.uiState.value
        assertEquals("Sítio Boa Vista", estado.nomePropriedade)
        assertEquals(450_000L - 210_000L, estado.saldoCentavos)
    }

    @Test
    fun `lancamento novo aparece no inicio sem ninguem pedir`() = runTest {
        val vm = inicio()
        lancamentos.salvar(
            Lancamento(
                propriedadeId = DadosDeExemplo.ID_FAZENDA,
                tipo = TipoLancamento.RECEITA, categoria = CategoriaLancamento.ARRENDAMENTO,
                valorCentavos = 50_000L, data = hoje
            )
        )

        assertEquals(1_615_000L, vm.uiState.value.saldoCentavos)
    }

    @Test
    fun `mes sem movimento deixa a barra meio a meio`() {
        val vm = inicio()
        repeat(3) { vm.aoAvancarMes() }

        assertEquals(0L, vm.uiState.value.saldoCentavos)
        assertEquals(0.5f, vm.uiState.value.proporcaoReceitas, 0.0001f)
    }

    @Test
    fun `sem nenhuma propriedade o inicio pede para cadastrar`() {
        val vm = InicioViewModel(
            LancamentoRepositoryMemoria(emptyList()), PropriedadeRepositoryMemoria(emptyList()),
            ClimaRepositoryMemoria(), AjustesAvisoMemoria(), hoje = { hoje }
        )
        assertFalse(vm.uiState.value.temPropriedade)
        assertFalse(vm.uiState.value.carregando)
    }

    @Test
    fun `RF09 - filtro de despesas esconde receitas mas o saldo continua o do mes`() {
        val vm = lista()
        vm.aoMudarFiltro(FiltroLancamento.DESPESAS)

        val estado = vm.uiState.value
        assertEquals(3, estado.quantidade)
        assertTrue(estado.lancamentos.all { it.tipo == TipoLancamento.DESPESA })
        assertEquals("filtrar não pode mudar o saldo", 1_565_000L, estado.saldoCentavos)
    }

    @Test
    fun `lista vem da data mais recente para a mais antiga`() {
        val datas = lista().uiState.value.lancamentos.map { it.data }
        assertEquals(datas.sortedDescending(), datas)
    }

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
                valorCentavos = 2_000_000L, data = hoje
            )
        )
        val sitio = PropriedadeListaViewModel(propriedades, lancamentos).uiState.value
            .itens.first { it.propriedade.id == DadosDeExemplo.ID_SITIO }

        assertTrue(sitio.saldoCentavos < 0)
        assertEquals(1f, sitio.proporcao, 0.0001f)
    }

    @Test
    fun `erro ao ler as propriedades vira mensagem amigavel e a lista volta sozinha`() = runTest {
        var leituras = 0
        val instavel = object : PropriedadeRepository by propriedades {
            override fun observarTodas(): Flow<List<Propriedade>> = flow {
                if (leituras++ == 0) throw IllegalStateException("SQLiteDatabaseLockedException: database is locked")
                emitAll(propriedades.observarTodas())
            }
        }
        val vm = PropriedadeListaViewModel(instavel, lancamentos)

        assertEquals("Não foi possível carregar as propriedades. Tentando de novo…", vm.uiState.value.erro)

        advanceUntilIdle()
        assertNull(vm.uiState.value.erro)
        assertEquals(2, vm.uiState.value.itens.size)
    }

    @Test
    fun `RF09 - filtrar por categoria mostra so ela e soma o que aparece`() {
        val vm = lista()
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
        val categorias = lista().uiState.value.categoriasDoMes
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
        val vm = lista()
        vm.aoMudarFiltro(FiltroLancamento.DESPESAS)
        assertTrue(vm.uiState.value.categoriasDoMes.none { it.tipo == TipoLancamento.RECEITA })
    }

    @Test
    fun `trocar para receitas desmarca uma categoria de despesa`() {
        val vm = lista()
        vm.aoMudarCategoria(CategoriaLancamento.ADUBO)
        vm.aoMudarFiltro(FiltroLancamento.RECEITAS)

        assertEquals(null, vm.uiState.value.categoria)
        assertEquals(1, vm.uiState.value.quantidade)
    }

    @Test
    fun `trocar para despesas desmarca uma categoria de receita`() {
        val vm = lista()
        vm.aoMudarCategoria(CategoriaLancamento.VENDA_SAFRA)
        vm.aoMudarFiltro(FiltroLancamento.DESPESAS)

        assertEquals(null, vm.uiState.value.categoria)
        assertEquals(3, vm.uiState.value.quantidade)
    }

    @Test
    fun `trocar para despesas mantem uma categoria de despesa marcada`() {
        val vm = lista()
        vm.aoMudarCategoria(CategoriaLancamento.ADUBO)
        vm.aoMudarFiltro(FiltroLancamento.DESPESAS)
        assertEquals(CategoriaLancamento.ADUBO, vm.uiState.value.categoria)
    }

    @Test
    fun `tocar de novo na categoria marcada desmarca, e limpar tira tudo`() {
        val vm = lista()
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
        val vm = lista()
        vm.aoMudarCategoria(CategoriaLancamento.ADUBO)
        vm.aoVoltarMes()

        val estado = vm.uiState.value
        assertEquals(0, estado.quantidade)
        assertTrue("senão não daria para desmarcar", CategoriaLancamento.ADUBO in estado.categoriasDoMes)
    }

    @Test
    fun `o mes escolhido no inicio e o mesmo em lancamentos e sitios`() {
        val inicio = inicio()
        val lista = lista()
        val sitios = PropriedadeListaViewModel(propriedades, lancamentos)

        inicio.aoVoltarMes()

        val mesPassado = mesDeHoje.minusMonths(1)
        assertEquals(mesPassado, lista.uiState.value.mes)
        assertEquals(mesPassado, sitios.uiState.value.mes)
        assertEquals(2, lista.uiState.value.quantidade)
    }
}
