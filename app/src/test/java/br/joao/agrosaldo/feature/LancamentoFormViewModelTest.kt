package br.joao.agrosaldo.feature

import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.core.util.paraLocalDateOuNulo
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.repository.LancamentoRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.feature.lancamento.LancamentoFormViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LancamentoFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var propriedades: PropriedadeRepositoryMemoria
    private lateinit var lancamentos: LancamentoRepositoryMemoria

    @Before
    fun preparar() {
        propriedades = PropriedadeRepositoryMemoria()
        lancamentos = LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(LocalDate.of(2026, 9, 30)))
        SelecaoAtual.selecionar(DadosDeExemplo.ID_FAZENDA)
    }

    private fun novo() = LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = null)

    @Test
    fun `formulario novo ja vem com a propriedade selecionada e a data de hoje`() {
        val estado = novo().uiState.value
        assertEquals(DadosDeExemplo.ID_FAZENDA, estado.propriedadeId)
        assertEquals(LocalDate.now(), estado.data.paraLocalDateOuNulo())
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
    fun `excluir remove o lancamento`() = runTest {
        val vm = LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = "exemplo-3")
        vm.aoExcluir()

        assertTrue(vm.uiState.value.excluido)
        assertEquals(null, lancamentos.buscarPorId("exemplo-3"))
    }
}
