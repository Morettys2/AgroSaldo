package br.joao.agrosaldo.feature

import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.core.navigation.PrivacidadeViewModel
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.preferencias.PrivacidadeMemoria
import br.joao.agrosaldo.data.repository.LancamentoRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.feature.lancamento.FiltroLancamento
import br.joao.agrosaldo.feature.lancamento.LancamentoListaViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Lista de lançamentos: agrupamento por dia, busca em todos os meses, pílulas de mês do
 * cabeçalho e ocultação de valores (PrivacidadeViewModel).
 */
class LancamentoListaBuscaTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var propriedades: PropriedadeRepositoryMemoria
    private lateinit var exemplos: LancamentoRepositoryMemoria

    /** Dia fixo: com o relógio real, os testes poderiam falhar na virada do mês. */
    private val hoje = LocalDate.of(2026, 9, 30)
    private val mesDeHoje = YearMonth.from(hoje)

    @Before
    fun preparar() {
        propriedades = PropriedadeRepositoryMemoria()
        exemplos = LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(hoje))
        SelecaoAtual.limpar()
        SelecaoAtual.selecionar(DadosDeExemplo.ID_FAZENDA)
        SelecaoAtual.selecionarMes(mesDeHoje)
    }

    private fun lista() = LancamentoListaViewModel(exemplos, propriedades, hoje = { hoje })

    /** Dia fixo, para "Hoje" e "Ontem" não dependerem do dia em que o teste roda. */
    private val quinzeDeSetembro = LocalDate.of(2026, 9, 15)

    private fun lancamento(id: String, data: LocalDate, tipo: TipoLancamento = TipoLancamento.DESPESA) = Lancamento(
        id = id,
        propriedadeId = DadosDeExemplo.ID_FAZENDA,
        tipo = tipo,
        categoria = if (tipo == TipoLancamento.DESPESA) CategoriaLancamento.ADUBO else CategoriaLancamento.VENDA_SAFRA,
        valorCentavos = 10_000L,
        data = data
    )

    private fun listaDeSetembro(): LancamentoListaViewModel {
        val repo = LancamentoRepositoryMemoria(
            listOf(
                lancamento("a", quinzeDeSetembro.minusDays(5)),
                lancamento("b", quinzeDeSetembro),
                lancamento("c", quinzeDeSetembro.minusDays(1), TipoLancamento.RECEITA),
                lancamento("d", quinzeDeSetembro.minusDays(5), TipoLancamento.RECEITA)
            )
        )
        SelecaoAtual.selecionarMes(YearMonth.of(2026, 9))
        return LancamentoListaViewModel(repo, propriedades, hoje = { quinzeDeSetembro })
    }

    @Test
    fun `lista sai separada por dia, do mais novo para o mais antigo, com o nome do dia`() {
        val grupos = listaDeSetembro().uiState.value.grupos

        assertEquals(listOf("Hoje", "Ontem", "10 de setembro"), grupos.map { it.rotulo })
        assertEquals(listOf(1, 1, 2), grupos.map { it.lancamentos.size })
    }

    @Test
    fun `os grupos tem exatamente os mesmos lancamentos da lista, na mesma ordem`() {
        val estado = listaDeSetembro().uiState.value
        assertEquals(estado.lancamentos, estado.grupos.flatMap { it.lancamentos })
    }

    @Test
    fun `filtro vale tambem para os grupos e some o dia que ficou vazio`() {
        val vm = listaDeSetembro()
        vm.aoMudarFiltro(FiltroLancamento.RECEITAS)
        assertEquals(listOf("Ontem", "10 de setembro"), vm.uiState.value.grupos.map { it.rotulo })
    }

    @Test
    fun `busca acha lancamento de outro mes`() {
        val vm = lista()
        vm.aoBuscar("diarista")

        val estado = vm.uiState.value
        assertTrue(estado.buscando)
        assertEquals(listOf("exemplo-5"), estado.lancamentos.map { it.id })
        assertEquals("o mês escolhido não muda", mesDeHoje, estado.mes)
    }

    @Test
    fun `busca ignora acento e maiuscula, nos dois sentidos`() {
        val vm = lista()
        vm.aoBuscar("PIVO")
        assertEquals(listOf("exemplo-2"), vm.uiState.value.lancamentos.map { it.id })

        vm.aoBuscar("  Diarísta ")
        assertEquals(listOf("exemplo-5"), vm.uiState.value.lancamentos.map { it.id })
    }

    @Test
    fun `busca tambem procura no nome da categoria`() {
        val vm = lista()
        vm.aoBuscar("eletrica")
        assertTrue(vm.uiState.value.lancamentos.isNotEmpty())
        assertTrue(vm.uiState.value.lancamentos.all { it.categoria == CategoriaLancamento.ENERGIA })
    }

    @Test
    fun `busca respeita o filtro de tipo`() {
        val vm = lista()
        vm.aoBuscar("venda")
        assertTrue(vm.uiState.value.quantidade >= 2)

        vm.aoMudarFiltro(FiltroLancamento.DESPESAS)
        assertEquals(0, vm.uiState.value.quantidade)
    }

    @Test
    fun `busca fica so na propriedade escolhida`() {
        val vm = lista()
        vm.aoBuscar("a")
        val resultado = vm.uiState.value.lancamentos
        assertTrue("o termo precisa achar algo, senão o all passa vazio", resultado.isNotEmpty())
        assertTrue(resultado.all { it.propriedadeId == DadosDeExemplo.ID_FAZENDA })

        vm.aoBuscar("conserto") // "Conserto da bomba" é do Sítio.
        assertTrue(vm.uiState.value.lancamentos.isEmpty())
        vm.aoSelecionarPropriedade(DadosDeExemplo.ID_SITIO)
        assertEquals(listOf("exemplo-9"), vm.uiState.value.lancamentos.map { it.id })
    }

    @Test
    fun `fechar a busca volta para o mes, e so espacos nao contam como busca`() {
        val vm = lista()
        vm.aoBuscar("diarista")
        vm.aoFecharBusca()
        assertFalse(vm.uiState.value.buscando)
        assertEquals(4, vm.uiState.value.quantidade)

        vm.aoBuscar("   ")
        assertFalse(vm.uiState.value.buscando)
        assertEquals(4, vm.uiState.value.quantidade)
    }

    @Test
    fun `limpar filtros nao apaga o que foi digitado na busca`() {
        val vm = lista()
        vm.aoBuscar("venda")
        vm.aoMudarFiltro(FiltroLancamento.RECEITAS)
        vm.aoLimparFiltros()

        assertEquals("venda", vm.uiState.value.busca)
        assertFalse(vm.uiState.value.temFiltro)
    }

    @Test
    fun `saldo e totais do cabecalho continuam os do mes durante a busca`() {
        val vm = lista()
        vm.aoBuscar("diarista")
        val estado = vm.uiState.value
        assertEquals(1_565_000L, estado.saldoCentavos)
        assertEquals(1_800_000L, estado.receitasCentavos)
        assertEquals(235_000L, estado.despesasCentavos)
    }

    @Test
    fun `pilulas vao de dois anos atras ate um ano a frente`() {
        val hoje = LocalDate.of(2026, 10, 4)
        val meses = LancamentoListaViewModel.mesesDoCabecalho(YearMonth.of(2026, 10), hoje)
        assertEquals(YearMonth.of(2024, 10), meses.first())
        assertEquals(YearMonth.of(2027, 10), meses.last())
        assertEquals(37, meses.size)
        assertEquals("sem buraco nem repetição", meses.sorted().distinct(), meses)
    }

    @Test
    fun `mes escolhido fora da faixa estica as pilulas para incluir ele`() {
        val hoje = LocalDate.of(2026, 10, 4)
        val antigo = YearMonth.of(2020, 1)
        assertEquals(antigo, LancamentoListaViewModel.mesesDoCabecalho(antigo, hoje).first())
    }

    @Test
    fun `tocar numa pilula troca o mes de todas as abas`() {
        val vm = lista()
        val mesPassado = mesDeHoje.minusMonths(1)
        vm.aoSelecionarMes(mesPassado)
        assertEquals(mesPassado, SelecaoAtual.mes.value)
        assertEquals(mesPassado, vm.uiState.value.mes)
    }

    @Test
    fun `olho esconde e mostra de novo, e o repositorio guarda a escolha`() {
        val repositorio = PrivacidadeMemoria()
        val vm = PrivacidadeViewModel(repositorio)
        assertFalse(vm.valoresOcultos.value)

        vm.aoAlternar()
        assertTrue(vm.valoresOcultos.value)
        assertTrue(repositorio.valoresOcultos.value)

        vm.aoAlternar()
        assertFalse(vm.valoresOcultos.value)
    }

    @Test
    fun `quem escondeu continua escondido ao abrir o app de novo`() {
        assertTrue(PrivacidadeViewModel(PrivacidadeMemoria(inicial = true)).valoresOcultos.value)
    }

    @Test
    fun `mudar o mes por fora fecha a busca, para o mes escolhido aparecer`() {
        val vm = lista()
        vm.aoBuscar("diarista")
        SelecaoAtual.voltarMes() // Mudança vinda de outra aba (ex.: toque num mês do Histórico).

        assertFalse(vm.uiState.value.buscando)
        assertEquals(2, vm.uiState.value.quantidade)
    }

    @Test
    fun `busca junto com filtro de categoria`() {
        val vm = lista()
        vm.aoBuscar("a")
        vm.aoMudarCategoria(CategoriaLancamento.ENERGIA)
        val estado = vm.uiState.value
        assertTrue(estado.lancamentos.isNotEmpty())
        assertTrue(estado.lancamentos.all { it.categoria == CategoriaLancamento.ENERGIA })
    }

    @Test
    fun `durante a busca as categorias oferecidas sao as dos resultados`() {
        val vm = lista()
        vm.aoBuscar("diarista")
        assertEquals(listOf(CategoriaLancamento.MAO_DE_OBRA), vm.uiState.value.categoriasDoMes)
    }

    @Test
    fun `voltar para a tela depois da meia-noite troca hoje por ontem`() {
        var hoje = quinzeDeSetembro
        val repo = LancamentoRepositoryMemoria(listOf(lancamento("b", quinzeDeSetembro)))
        SelecaoAtual.selecionarMes(YearMonth.of(2026, 9))
        val vm = LancamentoListaViewModel(repo, propriedades, hoje = { hoje })
        assertEquals("Hoje", vm.uiState.value.grupos.single().rotulo)

        hoje = quinzeDeSetembro.plusDays(1)
        vm.aoVoltarParaATela()
        assertEquals("Ontem", vm.uiState.value.grupos.single().rotulo)
    }
}
