package br.joao.agrosaldo.feature

import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.core.util.paraLocalDateOuNulo
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.repository.LancamentoRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.feature.lancamento.LancamentoFormViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LancamentoFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var propriedades: PropriedadeRepositoryMemoria
    private lateinit var lancamentos: LancamentoRepositoryMemoria

    @Before
    fun preparar() {
        propriedades = PropriedadeRepositoryMemoria()
        lancamentos = LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(LocalDate.of(2026, 9, 30)))
        SelecaoAtual.limpar()
        SelecaoAtual.selecionar(DadosDeExemplo.ID_FAZENDA)
    }

    private val hoje = LocalDate.of(2026, 10, 4)
    private val desfazer = Desfazer()

    private fun novo() = LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = null, hoje = { hoje })

    @Test
    fun `formulario novo ja vem com a propriedade selecionada e a data de hoje`() {
        val estado = novo().uiState.value
        assertEquals(DadosDeExemplo.ID_FAZENDA, estado.propriedadeId)
        assertEquals(hoje, estado.data.paraLocalDateOuNulo())
        assertEquals(TipoLancamento.DESPESA, estado.tipo)
    }

    @Test
    fun `trocar para receita troca a categoria junto`() {
        val vm = novo()
        vm.aoMudarCategoria(CategoriaLancamento.ADUBO)

        vm.aoMudarTipo(TipoLancamento.RECEITA)

        assertEquals(TipoLancamento.RECEITA, vm.uiState.value.tipo)
        assertEquals(CategoriaLancamento.VENDA_SAFRA, vm.uiState.value.categoria)
        assertTrue(vm.uiState.value.categoriasDisponiveis.all { it.tipo == TipoLancamento.RECEITA })
    }

    @Test
    fun `campo de valor aceita so digitos e mostra em centavos`() {
        val vm = novo()
        vm.aoMudarValor("R$ 1.250,50")

        assertEquals("125050", vm.uiState.value.valorDigitos)
        assertEquals(125_050L, vm.uiState.value.valorCentavos)
    }

    @Test
    fun `valor vazio e data impossivel geram erro e nao gravam`() = runTest {
        val antes = lancamentos.observarTodos().first().size
        val vm = novo()
        vm.aoMudarData("31/02/2026")
        vm.aoSalvar()

        assertNotNull(vm.uiState.value.erroValor)
        assertNotNull(vm.uiState.value.erroData)
        assertFalse(vm.uiState.value.salvo)
        assertEquals(antes, lancamentos.observarTodos().first().size)
    }

    @Test
    fun `salvar grava o lancamento em centavos na propriedade escolhida`() = runTest {
        val vm = novo()
        vm.aoMudarPropriedade(DadosDeExemplo.ID_SITIO)
        vm.aoMudarCategoria(CategoriaLancamento.COMBUSTIVEL)
        vm.aoMudarValor("32000")
        vm.aoMudarData("28/09/2026")
        vm.aoMudarDescricao("  Diesel do trator  ")
        vm.aoSalvar()

        assertTrue(vm.uiState.value.salvo)
        val gravado = lancamentos.observarTodos().first().first { it.descricao == "Diesel do trator" }
        assertEquals(32_000L, gravado.valorCentavos)
        assertEquals(DadosDeExemplo.ID_SITIO, gravado.propriedadeId)
        assertEquals(LocalDate.of(2026, 9, 28), gravado.data)
        assertEquals("a propriedade do lançamento vira a selecionada", DadosDeExemplo.ID_SITIO, SelecaoAtual.propriedadeId.value)
        assertEquals("e o mês dele também, para ele não sumir da lista", YearMonth.of(2026, 9), SelecaoAtual.mes.value)
    }

    @Test
    fun `edicao carrega o lancamento e salvar nao duplica`() = runTest {
        val total = lancamentos.observarTodos().first().size
        val vm = LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = "exemplo-2")

        assertTrue(vm.uiState.value.ehEdicao)
        assertEquals("125000", vm.uiState.value.valorDigitos)
        assertEquals(CategoriaLancamento.ENERGIA, vm.uiState.value.categoria)

        vm.aoMudarValor("130000")
        vm.aoSalvar()

        assertEquals(total, lancamentos.observarTodos().first().size)
        assertEquals(130_000L, lancamentos.buscarPorId("exemplo-2")?.valorCentavos)
    }

    @Test
    fun `excluir remove na hora e oferece desfazer, que traz de volta igual`() = runTest {
        val original = lancamentos.buscarPorId("exemplo-3")
        val vm = LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = "exemplo-3", desfazer = desfazer)
        vm.aoExcluir()

        assertTrue(vm.uiState.value.excluido)
        assertNull(lancamentos.buscarPorId("exemplo-3"))
        val oferta = desfazer.pendente.value
        assertEquals("Lançamento excluído", oferta?.mensagem)

        desfazer.desfazer(checkNotNull(oferta))

        assertEquals(original, lancamentos.buscarPorId("exemplo-3"))
    }

    // ---------- calendário e atalhos ----------

    @Test
    fun `atalhos hoje e ontem preenchem a data`() {
        val vm = novo()
        vm.aoUsarOntem()
        assertEquals("03/10/2026", vm.uiState.value.data)
        vm.aoUsarHoje()
        assertEquals("04/10/2026", vm.uiState.value.data)
    }

    @Test
    fun `data escolhida no calendario limpa o erro da data`() {
        val vm = novo()
        vm.aoMudarData("99/99/9999")
        vm.aoSalvar()
        assertNotNull(vm.uiState.value.erroData)

        vm.aoEscolherData(LocalDate.of(2026, 9, 15))

        assertEquals("15/09/2026", vm.uiState.value.data)
        assertNull(vm.uiState.value.erroData)
    }

    @Test
    fun `RN07 - data alem de 12 meses mostra o erro no campo da data`() = runTest {
        val vm = novo()
        vm.aoMudarValor("1000")
        vm.aoMudarData("05/10/2027")
        vm.aoSalvar()

        assertEquals("A data pode ser no máximo 12 meses à frente.", vm.uiState.value.erroData)
        assertFalse(vm.uiState.value.salvo)

        vm.aoMudarData("04/10/2027")
        vm.aoSalvar()
        assertTrue("exatamente 12 meses ainda vale", vm.uiState.value.salvo)
    }

    // ---------- lançar de novo ----------

    @Test
    fun `lancar de novo copia tudo menos a data, que vira hoje`() = runTest {
        val total = lancamentos.observarTodos().first().size
        val vm = LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = null, copiaDe = "exemplo-2", hoje = { hoje })

        val estado = vm.uiState.value
        assertFalse("é um lançamento novo, não uma edição", estado.ehEdicao)
        assertEquals(CategoriaLancamento.ENERGIA, estado.categoria)
        assertEquals("125000", estado.valorDigitos)
        assertEquals("Conta do pivô", estado.descricao)
        assertEquals("04/10/2026", estado.data)

        vm.aoSalvar()

        assertEquals("criou outro, não mexeu no original", total + 1, lancamentos.observarTodos().first().size)
        assertEquals(LocalDate.of(2026, 9, 26), lancamentos.buscarPorId("exemplo-2")?.data)
    }

    // ---------- propriedade escolhida ----------

    @Test
    fun `propriedade escolhida que nao existe mais cai na primeira da lista`() {
        SelecaoAtual.selecionar("propriedade-apagada")
        val estado = novo().uiState.value
        assertEquals(DadosDeExemplo.ID_FAZENDA, estado.propriedadeId)
        assertEquals("Fazenda São João da Serra", estado.nomePropriedadeSelecionada)
    }

    @Test
    fun `outras despesas aparece por ultimo entre as despesas`() {
        assertEquals(CategoriaLancamento.OUTRAS_DESPESAS, novo().uiState.value.categoriasDisponiveis.last())
    }

    // ---------- R4: teclado numérico da tela ----------

    @Test
    fun `teclado entra pela direita, como a maquininha de cartao`() {
        val vm = novo()
        vm.aoTeclar("3")
        vm.aoTeclar("2")
        vm.aoTeclar("00")
        vm.aoTeclar("0")

        assertEquals("32000", vm.uiState.value.valorDigitos)
        assertEquals(32_000L, vm.uiState.value.valorCentavos)
    }

    @Test
    fun `zero a esquerda nao entra`() {
        val vm = novo()
        vm.aoTeclar("0")
        vm.aoTeclar("00")
        assertEquals("", vm.uiState.value.valorDigitos)

        vm.aoTeclar("5")
        assertEquals("5", vm.uiState.value.valorDigitos)
    }

    @Test
    fun `tecla que passaria de 9 digitos e ignorada inteira`() {
        val vm = novo()
        repeat(8) { vm.aoTeclar("9") }
        vm.aoTeclar("00")
        assertEquals("não entra pela metade", "99999999", vm.uiState.value.valorDigitos)

        vm.aoTeclar("1")
        assertEquals("999999991", vm.uiState.value.valorDigitos)
        vm.aoTeclar("1")
        assertEquals(LancamentoFormViewModel.MAXIMO_DE_DIGITOS, vm.uiState.value.valorDigitos.length)
    }

    @Test
    fun `tecla que nao e numero e ignorada`() {
        val vm = novo()
        vm.aoTeclar("1")
        vm.aoTeclar(",")
        vm.aoTeclar("")
        assertEquals("1", vm.uiState.value.valorDigitos)
    }

    @Test
    fun `apagar tira o ultimo e segurar apaga tudo`() {
        val vm = novo()
        vm.aoTeclar("125")
        vm.aoApagarDigito()
        assertEquals("12", vm.uiState.value.valorDigitos)

        vm.aoLimparValor()
        assertEquals("", vm.uiState.value.valorDigitos)

        vm.aoApagarDigito()
        assertEquals("apagar com nada não quebra", "", vm.uiState.value.valorDigitos)
    }

    @Test
    fun `digitar depois do erro de valor tira o erro`() {
        val vm = novo()
        vm.aoSalvar()
        assertNotNull(vm.uiState.value.erroValor)

        vm.aoTeclar("1")
        assertNull(vm.uiState.value.erroValor)

        vm.aoApagarDigito()
        vm.aoSalvar()
        assertNotNull(vm.uiState.value.erroValor)
        vm.aoLimparValor()
        assertNull(vm.uiState.value.erroValor)
    }

    // ---------- R4: atalhos Despesa e Receita do Início ----------

    @Test
    fun `atalho receita abre o formulario ja em receita`() {
        val vm = LancamentoFormViewModel(
            lancamentos, propriedades, lancamentoId = null, hoje = { hoje }, tipoInicial = TipoLancamento.RECEITA
        )
        assertEquals(TipoLancamento.RECEITA, vm.uiState.value.tipo)
        assertEquals(CategoriaLancamento.VENDA_SAFRA, vm.uiState.value.categoria)
        assertEquals(DadosDeExemplo.ID_FAZENDA, vm.uiState.value.propriedadeId)
    }

    @Test
    fun `na edicao o tipo do lancamento manda, nao o do atalho`() {
        val vm = LancamentoFormViewModel(
            lancamentos, propriedades, lancamentoId = "exemplo-2", hoje = { hoje }, tipoInicial = TipoLancamento.RECEITA
        )
        assertEquals(TipoLancamento.DESPESA, vm.uiState.value.tipo)
        assertEquals(CategoriaLancamento.ENERGIA, vm.uiState.value.categoria)
    }

    // ---------- R4: textos da tela ----------

    @Test
    fun `campo de data mostra hoje e ontem com o dia`() {
        val vm = novo()
        assertEquals("Hoje, 4 out", vm.uiState.value.rotuloData)
        vm.aoUsarOntem()
        assertEquals("Ontem, 3 out", vm.uiState.value.rotuloData)
        vm.aoEscolherData(LocalDate.of(2026, 9, 28))
        assertEquals("28 set", vm.uiState.value.rotuloData)
        vm.aoEscolherData(LocalDate.of(2025, 12, 25))
        assertEquals("25 dez 2025", vm.uiState.value.rotuloData)
    }

    @Test
    fun `linha de resumo junta categoria, dia e sitio`() {
        val vm = novo()
        vm.aoMudarCategoria(CategoriaLancamento.COMBUSTIVEL)
        assertEquals("Combustível · Hoje · Fazenda São João da Serra", vm.uiState.value.resumo)
    }

    @Test
    fun `data digitada invalida aparece como foi escrita, sem quebrar`() {
        val vm = novo()
        vm.aoMudarData("31/02/2026")
        assertEquals("31/02/2026", vm.uiState.value.rotuloData)
        assertEquals("sem o dia no resumo", "Energia elétrica · Fazenda São João da Serra", vm.uiState.value.resumo)
    }

    // ---------- R4: revisão ----------

    /** Grava só quando o teste deixa: simula o banco demorando. */
    private class LancamentosLentos(private val real: LancamentoRepositoryMemoria) : LancamentoRepository by real {
        val portao = CompletableDeferred<Unit>()
        override suspend fun salvar(lancamento: Lancamento) {
            portao.await()
            real.salvar(lancamento)
        }
    }

    @Test
    fun `dois toques em salvar gravam um lancamento so`() = runTest {
        val lentos = LancamentosLentos(lancamentos)
        val antes = lancamentos.observarTodos().first().size
        val vm = LancamentoFormViewModel(lentos, propriedades, lancamentoId = null, hoje = { hoje })
        vm.aoTeclar("5000")

        vm.aoSalvar()
        assertTrue("o botão desliga enquanto grava", vm.uiState.value.salvando)
        vm.aoSalvar()

        lentos.portao.complete(Unit)
        assertTrue(vm.uiState.value.salvo)
        assertEquals(antes + 1, lancamentos.observarTodos().first().size)
    }

    @Test
    fun `se gravar falhar o botao volta a funcionar`() {
        val quebrado = object : LancamentoRepository by lancamentos {
            override suspend fun salvar(lancamento: Lancamento) = throw IllegalArgumentException("Banco recusou.")
        }
        val vm = LancamentoFormViewModel(quebrado, propriedades, lancamentoId = null, hoje = { hoje })
        vm.aoTeclar("5000")
        vm.aoSalvar()

        assertEquals("Banco recusou.", vm.uiState.value.erroGeral)
        assertFalse(vm.uiState.value.salvando)
        assertFalse(vm.uiState.value.salvo)
    }

    @Test
    fun `teclado nao funciona antes de carregar, para o valor do banco nao apagar o digitado`() {
        val aindaCarregando = MutableSharedFlow<List<Propriedade>>(replay = 1)
        val lentas = object : PropriedadeRepository by propriedades {
            override fun observarTodas(): Flow<List<Propriedade>> = aindaCarregando
        }
        val vm = LancamentoFormViewModel(lancamentos, lentas, lancamentoId = null, hoje = { hoje })
        assertFalse(vm.uiState.value.carregado)

        vm.aoTeclar("5")
        assertEquals("", vm.uiState.value.valorDigitos)

        aindaCarregando.tryEmit(DadosDeExemplo.propriedades())
        assertTrue(vm.uiState.value.carregado)
        vm.aoTeclar("5")
        assertEquals("5", vm.uiState.value.valorDigitos)
    }

    @Test
    fun `lancar de novo usa o tipo do original, nao o do atalho`() {
        val vm = LancamentoFormViewModel(
            lancamentos, propriedades, lancamentoId = null, copiaDe = "exemplo-1", hoje = { hoje },
            tipoInicial = TipoLancamento.DESPESA
        )
        assertEquals(TipoLancamento.RECEITA, vm.uiState.value.tipo)
        assertFalse(vm.uiState.value.ehEdicao)
    }

    @Test
    fun `campo de data mostra amanha com o dia`() {
        val vm = novo()
        vm.aoEscolherData(hoje.plusDays(1))
        assertEquals("Amanhã, 5 out", vm.uiState.value.rotuloData)
    }

    @Test
    fun `sem nenhum sitio o formulario sabe que carregou e esta vazio`() {
        val vm = LancamentoFormViewModel(lancamentos, PropriedadeRepositoryMemoria(emptyList()), lancamentoId = null, hoje = { hoje })
        assertTrue(vm.uiState.value.carregado)
        assertTrue(vm.uiState.value.propriedades.isEmpty())
    }
}
