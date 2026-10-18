package br.joao.agrosaldo.feature

import androidx.lifecycle.SavedStateHandle
import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.core.util.paraLocalDateOuNulo
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.DespachantesDeTeste
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.relogioEm
import br.joao.agrosaldo.data.repository.LancamentoRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.feature.lancamento.LancamentoFormViewModel
import br.joao.agrosaldo.feature.lancamento.LancamentoListaViewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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

class LancamentoFormViewModelTest {

    /** Propriedade e mês escolhidos; cada teste tem a sua, sem estado global. */
    private val selecao = SelecaoAtual()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var propriedades: PropriedadeRepositoryMemoria
    private lateinit var lancamentos: LancamentoRepositoryMemoria

    @Before
    fun preparar() {
        propriedades = PropriedadeRepositoryMemoria()
        lancamentos = LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(LocalDate.of(2026, 9, 30)))
        selecao.limpar()
        selecao.selecionar(DadosDeExemplo.ID_FAZENDA)
    }

    private val hoje = LocalDate.of(2026, 10, 4)
    private val desfazer = Desfazer()

    private fun novo() =
        LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = null, relogio = relogioEm(hoje), selecao = selecao)

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
    fun `valor vazio gera erro e nao grava`() = runTest {
        val antes = lancamentos.observarTodos().first().size
        val vm = novo()
        vm.aoSalvar()

        assertNotNull(vm.uiState.value.erroValor)
        assertFalse(vm.uiState.value.salvo)
        assertEquals(antes, lancamentos.observarTodos().first().size)
    }

    @Test
    fun `data invalida restaurada aparece como estava e salvar pede a data de novo`() = runTest {
        val antes = lancamentos.observarTodos().first().size
        val restaurado = SavedStateHandle(mapOf(LancamentoFormViewModel.CHAVE_DATA to "31/02/2026"))
        val vm =
            LancamentoFormViewModel(
                lancamentos,
                propriedades,
                lancamentoId = null,
                relogio = relogioEm(hoje),
                estadoSalvo = restaurado,
                selecao = selecao
            )

        assertEquals("31/02/2026", vm.uiState.value.rotuloData)
        assertEquals("sem o dia no resumo", "Energia elétrica · Fazenda Santa Clara", vm.uiState.value.resumo)

        vm.aoTeclar("1000")
        vm.aoSalvar()
        assertEquals("Data inválida. Escolha a data de novo.", vm.uiState.value.erroData)
        assertEquals(antes, lancamentos.observarTodos().first().size)

        vm.aoUsarHoje()
        assertNull(vm.uiState.value.erroData)
    }

    @Test
    fun `salvar grava o lancamento em centavos na propriedade escolhida`() = runTest {
        val vm = novo()
        vm.aoMudarPropriedade(DadosDeExemplo.ID_SITIO)
        vm.aoMudarCategoria(CategoriaLancamento.COMBUSTIVEL)
        vm.aoTeclar("320")
        vm.aoTeclar("00")
        vm.aoEscolherData(LocalDate.of(2026, 9, 28))
        vm.aoMudarDescricao("  Diesel do trator  ")
        vm.aoSalvar()

        assertTrue(vm.uiState.value.salvo)
        val gravado = lancamentos.observarTodos().first().first { it.descricao == "Diesel do trator" }
        assertEquals(32_000L, gravado.valorCentavos)
        assertEquals(DadosDeExemplo.ID_SITIO, gravado.propriedadeId)
        assertEquals(LocalDate.of(2026, 9, 28), gravado.data)
        assertEquals("a propriedade do lançamento vira a selecionada", DadosDeExemplo.ID_SITIO, selecao.propriedadeId.value)
        assertEquals("e o mês dele também, para ele não sumir da lista", YearMonth.of(2026, 9), selecao.mes.value)
    }

    @Test
    fun `edicao carrega o lancamento e salvar nao duplica`() = runTest {
        val total = lancamentos.observarTodos().first().size
        val vm = LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = "exemplo-2", selecao = selecao)

        assertTrue(vm.uiState.value.ehEdicao)
        assertEquals("125000", vm.uiState.value.valorDigitos)
        assertEquals(CategoriaLancamento.ENERGIA, vm.uiState.value.categoria)

        vm.aoLimparValor()
        vm.aoTeclar("1300")
        vm.aoTeclar("00")
        vm.aoSalvar()

        assertEquals(total, lancamentos.observarTodos().first().size)
        assertEquals(130_000L, lancamentos.buscarPorId("exemplo-2")?.valorCentavos)
    }

    @Test
    fun `salvar edicao sem mudar o mes nao troca o mes escolhido nem fecha a busca`() = runTest {
        selecao.selecionarMes(YearMonth.of(2026, 10))
        val lista =
            LancamentoListaViewModel(
                lancamentos,
                propriedades,
                relogio = relogioEm(hoje),
                despachantes = DespachantesDeTeste(Dispatchers.Unconfined),
                selecao = selecao
            )
        lista.aoBuscar("pivo")
        // exemplo-2 é de setembro; a pessoa o achou pela busca, olhando outubro.
        val vm =
            LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = "exemplo-2", relogio = relogioEm(hoje), selecao = selecao)

        vm.aoTeclar("0")
        vm.aoSalvar()

        assertTrue(vm.uiState.value.salvo)
        assertEquals(1_250_000L, lancamentos.buscarPorId("exemplo-2")?.valorCentavos)
        assertEquals(YearMonth.of(2026, 10), selecao.mes.value)
        assertTrue("a busca continua aberta", lista.uiState.value.buscando)
    }

    @Test
    fun `salvar edicao que muda o mes leva a selecao para o mes novo`() = runTest {
        selecao.selecionarMes(YearMonth.of(2026, 9))
        val vm =
            LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = "exemplo-2", relogio = relogioEm(hoje), selecao = selecao)

        vm.aoEscolherData(LocalDate.of(2026, 10, 2))
        vm.aoSalvar()

        assertEquals("para ele não sumir da lista", YearMonth.of(2026, 10), selecao.mes.value)
    }

    @Test
    fun `editar lancamento que nao existe mais avisa e salvar nao o recria`() = runTest {
        val total = lancamentos.observarTodos().first().size
        val vm = LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = "apagado", relogio = relogioEm(hoje), selecao = selecao)

        assertTrue(vm.uiState.value.naoEncontrado)

        vm.aoTeclar("5000")
        vm.aoSalvar()

        assertFalse(vm.uiState.value.salvo)
        assertNull(lancamentos.buscarPorId("apagado"))
        assertEquals(total, lancamentos.observarTodos().first().size)
    }

    @Test
    fun `lancamento existente nao e marcado como nao encontrado`() {
        assertFalse(
            LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = "exemplo-2", selecao = selecao).uiState.value.naoEncontrado
        )
        assertFalse(novo().uiState.value.naoEncontrado)
    }

    @Test
    fun `excluir remove na hora e oferece desfazer, que traz de volta igual`() = runTest {
        val original = lancamentos.buscarPorId("exemplo-3")
        val vm = LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = "exemplo-3", desfazer = desfazer, selecao = selecao)
        vm.aoExcluir()

        assertTrue(vm.uiState.value.excluido)
        assertNull(lancamentos.buscarPorId("exemplo-3"))
        val oferta = desfazer.pendente.value
        assertEquals("Lançamento excluído", oferta?.mensagem)

        desfazer.desfazer(checkNotNull(oferta))

        assertEquals(original, lancamentos.buscarPorId("exemplo-3"))
    }

    @Test
    fun `descricao digitada ou colada nunca passa do limite`() {
        val vm = novo()

        vm.aoMudarDescricao("x".repeat(Lancamento.DESCRICAO_MAXIMA + 500))

        assertEquals(Lancamento.DESCRICAO_MAXIMA, vm.uiState.value.descricao.length)
    }

    @Test
    fun `excluir lancamento que ja nao existe nao oferece desfazer`() = runTest {
        val vm = LancamentoFormViewModel(lancamentos, propriedades, lancamentoId = "exemplo-3", desfazer = desfazer, selecao = selecao)
        lancamentos.excluir("exemplo-3")

        vm.aoExcluir()

        assertNull(desfazer.pendente.value)
    }

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
        vm.aoTeclar("1000")
        vm.aoEscolherData(hoje.plusMonths(13))
        vm.aoSalvar()
        assertNotNull(vm.uiState.value.erroData)

        vm.aoEscolherData(LocalDate.of(2026, 9, 15))

        assertEquals("15/09/2026", vm.uiState.value.data)
        assertNull(vm.uiState.value.erroData)
    }

    @Test
    fun `RN07 - data alem de 12 meses mostra o erro no campo da data`() = runTest {
        val vm = novo()
        vm.aoTeclar("1000")
        vm.aoEscolherData(LocalDate.of(2027, 10, 5))
        vm.aoSalvar()

        assertEquals("A data pode ser no máximo 12 meses à frente.", vm.uiState.value.erroData)
        assertFalse(vm.uiState.value.salvo)

        vm.aoEscolherData(LocalDate.of(2027, 10, 4))
        vm.aoSalvar()
        assertTrue("exatamente 12 meses ainda vale", vm.uiState.value.salvo)
    }

    @Test
    fun `lancar de novo copia tudo menos a data, que vira hoje`() = runTest {
        val total = lancamentos.observarTodos().first().size
        val vm =
            LancamentoFormViewModel(
                lancamentos,
                propriedades,
                lancamentoId = null,
                copiaDe = "exemplo-2",
                relogio = relogioEm(hoje),
                selecao = selecao
            )

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

    @Test
    fun `propriedade escolhida que nao existe mais cai na primeira da lista`() {
        selecao.selecionar("propriedade-apagada")
        val estado = novo().uiState.value
        assertEquals(DadosDeExemplo.ID_FAZENDA, estado.propriedadeId)
        assertEquals("Fazenda Santa Clara", estado.nomePropriedadeSelecionada)
    }

    @Test
    fun `outras despesas aparece por ultimo entre as despesas`() {
        assertEquals(CategoriaLancamento.OUTRAS_DESPESAS, novo().uiState.value.categoriasDisponiveis.last())
    }

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

    @Test
    fun `atalho receita abre o formulario ja em receita`() {
        val vm = LancamentoFormViewModel(
            lancamentos,
            propriedades,
            lancamentoId = null,
            relogio = relogioEm(hoje),
            tipoInicial = TipoLancamento.RECEITA,
            selecao = selecao
        )
        assertEquals(TipoLancamento.RECEITA, vm.uiState.value.tipo)
        assertEquals(CategoriaLancamento.VENDA_SAFRA, vm.uiState.value.categoria)
        assertEquals(DadosDeExemplo.ID_FAZENDA, vm.uiState.value.propriedadeId)
    }

    @Test
    fun `na edicao o tipo do lancamento manda, nao o do atalho`() {
        val vm = LancamentoFormViewModel(
            lancamentos,
            propriedades,
            lancamentoId = "exemplo-2",
            relogio = relogioEm(hoje),
            tipoInicial = TipoLancamento.RECEITA,
            selecao = selecao
        )
        assertEquals(TipoLancamento.DESPESA, vm.uiState.value.tipo)
        assertEquals(CategoriaLancamento.ENERGIA, vm.uiState.value.categoria)
    }

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
        assertEquals("Combustível · Hoje · Fazenda Santa Clara", vm.uiState.value.resumo)
    }

    /** Segura `salvar` até [portao] ser completado, simulando gravação lenta. */
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
        val vm = LancamentoFormViewModel(lentos, propriedades, lancamentoId = null, relogio = relogioEm(hoje), selecao = selecao)
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
        val vm = LancamentoFormViewModel(quebrado, propriedades, lancamentoId = null, relogio = relogioEm(hoje), selecao = selecao)
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
        val vm = LancamentoFormViewModel(lancamentos, lentas, lancamentoId = null, relogio = relogioEm(hoje), selecao = selecao)
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
            lancamentos,
            propriedades,
            lancamentoId = null,
            copiaDe = "exemplo-1",
            relogio = relogioEm(hoje),
            tipoInicial = TipoLancamento.DESPESA,
            selecao = selecao
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
        val vm =
            LancamentoFormViewModel(
                lancamentos,
                PropriedadeRepositoryMemoria(emptyList()),
                lancamentoId = null,
                relogio = relogioEm(hoje),
                selecao = selecao
            )
        assertTrue(vm.uiState.value.carregado)
        assertTrue(vm.uiState.value.propriedades.isEmpty())
    }

    @Test
    fun `o que foi preenchido volta depois que o Android mata o processo`() {
        val guardado = SavedStateHandle()
        val antes =
            LancamentoFormViewModel(
                lancamentos,
                propriedades,
                lancamentoId = null,
                relogio = relogioEm(hoje),
                estadoSalvo = guardado,
                selecao = selecao
            )
        antes.aoMudarTipo(TipoLancamento.RECEITA)
        antes.aoMudarCategoria(CategoriaLancamento.ARRENDAMENTO)
        antes.aoTeclar("4500")
        antes.aoEscolherData(LocalDate.of(2026, 9, 28))
        antes.aoMudarPropriedade(DadosDeExemplo.ID_SITIO)
        antes.aoMudarDescricao("Pasto do vizinho")

        // O handle vai para um Bundle: só tipos simples atravessam a morte do processo.
        assertTrue(guardado.keys().all { guardado.get<Any?>(it) is String })
        val restaurado = SavedStateHandle(guardado.keys().associateWith { guardado.get<Any?>(it) })
        val depois =
            LancamentoFormViewModel(
                lancamentos,
                propriedades,
                lancamentoId = null,
                relogio = relogioEm(hoje),
                estadoSalvo = restaurado,
                selecao = selecao
            )

        val estado = depois.uiState.value
        assertEquals(TipoLancamento.RECEITA, estado.tipo)
        assertEquals(CategoriaLancamento.ARRENDAMENTO, estado.categoria)
        assertEquals("4500", estado.valorDigitos)
        assertEquals("28/09/2026", estado.data)
        assertEquals(DadosDeExemplo.ID_SITIO, estado.propriedadeId)
        assertEquals("Pasto do vizinho", estado.descricao)
    }

    @Test
    fun `na edicao o valor restaurado vale mais que o do banco`() {
        val restaurado = SavedStateHandle(
            mapOf(
                LancamentoFormViewModel.CHAVE_VALOR to "99900",
                LancamentoFormViewModel.CHAVE_DESCRICAO to "Conta do pivô corrigida"
            )
        )
        val vm =
            LancamentoFormViewModel(
                lancamentos,
                propriedades,
                lancamentoId = "exemplo-2",
                relogio = relogioEm(hoje),
                estadoSalvo = restaurado,
                selecao = selecao
            )

        val estado = vm.uiState.value
        assertTrue(estado.ehEdicao)
        assertEquals("99900", estado.valorDigitos)
        assertEquals("Conta do pivô corrigida", estado.descricao)
        assertEquals("o que não foi guardado vem do banco", CategoriaLancamento.ENERGIA, estado.categoria)
        assertEquals("26/09/2026", estado.data)
    }

    @Test
    fun `restauracao ignora sitio que nao existe mais e categoria de outro tipo`() {
        val restaurado = SavedStateHandle(
            mapOf(
                LancamentoFormViewModel.CHAVE_TIPO to TipoLancamento.RECEITA.name,
                LancamentoFormViewModel.CHAVE_CATEGORIA to CategoriaLancamento.ADUBO.name,
                LancamentoFormViewModel.CHAVE_PROPRIEDADE to "sitio-excluido"
            )
        )
        val vm =
            LancamentoFormViewModel(
                lancamentos,
                propriedades,
                lancamentoId = null,
                relogio = relogioEm(hoje),
                estadoSalvo = restaurado,
                selecao = selecao
            )

        assertEquals(TipoLancamento.RECEITA, vm.uiState.value.tipo)
        assertEquals(CategoriaLancamento.VENDA_SAFRA, vm.uiState.value.categoria)
        assertEquals(DadosDeExemplo.ID_FAZENDA, vm.uiState.value.propriedadeId)
    }

    @Test
    fun `antes de carregar nada e guardado, para nao apagar o que seria restaurado`() {
        val aindaCarregando = MutableSharedFlow<List<Propriedade>>(replay = 1)
        val lentas = object : PropriedadeRepository by propriedades {
            override fun observarTodas(): Flow<List<Propriedade>> = aindaCarregando
        }
        val guardado = SavedStateHandle(mapOf(LancamentoFormViewModel.CHAVE_VALOR to "777"))
        val vm =
            LancamentoFormViewModel(
                lancamentos,
                lentas,
                lancamentoId = null,
                relogio = relogioEm(hoje),
                estadoSalvo = guardado,
                selecao = selecao
            )

        vm.aoMudarTipo(TipoLancamento.RECEITA)
        assertEquals("777", guardado.get<String>(LancamentoFormViewModel.CHAVE_VALOR))

        aindaCarregando.tryEmit(DadosDeExemplo.propriedades())
        assertEquals("777", vm.uiState.value.valorDigitos)
    }
}
