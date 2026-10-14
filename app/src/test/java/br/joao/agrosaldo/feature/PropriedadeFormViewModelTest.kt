package br.joao.agrosaldo.feature

import androidx.lifecycle.SavedStateHandle
import br.joao.agrosaldo.MainDispatcherRule
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.repository.LancamentoRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.feature.propriedade.PropriedadeFormViewModel
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
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

class PropriedadeFormViewModelTest {

    /** Propriedade e mês escolhidos; cada teste tem a sua, sem estado global. */
    private val selecao = SelecaoAtual()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var propriedades: PropriedadeRepositoryMemoria
    private lateinit var lancamentos: LancamentoRepositoryMemoria

    @Before
    fun preparar() {
        lancamentos = LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(LocalDate.of(2026, 9, 30)))
        propriedades = PropriedadeRepositoryMemoria(lancamentos = lancamentos)
    }

    private fun novo() = PropriedadeFormViewModel(propriedades, propriedadeId = null, selecao = selecao)
    private fun editando(id: String) = PropriedadeFormViewModel(propriedades, propriedadeId = id, selecao = selecao)

    private fun preencher(vm: PropriedadeFormViewModel, area: String = "3") {
        vm.aoMudarNome("Sítio Novo")
        vm.aoMudarArea(area)
        vm.aoMudarLatitude("-21")
        vm.aoMudarLongitude("-46")
    }

    @Test
    fun `editar propriedade que nao existe mais avisa e salvar nao recria o registro`() = runTest {
        val vm = editando("apagada")
        assertTrue(vm.uiState.value.naoEncontrado)

        preencher(vm)
        vm.aoSalvar()

        assertFalse(vm.uiState.value.salvo)
        assertNull(propriedades.buscarPorId("apagada"))
        assertEquals(2, propriedades.observarTodas().first().size)
    }

    @Test
    fun `propriedade existente e cadastro novo nao sao marcados como nao encontrados`() {
        assertFalse(editando(DadosDeExemplo.ID_SITIO).uiState.value.naoEncontrado)
        assertFalse(novo().uiState.value.naoEncontrado)
    }

    @Test
    fun `salvar com tudo vazio marca os tres erros e nao grava nada`() = runTest {
        val vm = novo()
        vm.aoSalvar()

        val estado = vm.uiState.value
        assertNotNull(estado.erroNome)
        assertNotNull(estado.erroArea)
        assertNotNull(estado.erroCoordenadas)
        assertFalse(estado.salvo)
        assertEquals(2, propriedades.observarTodas().first().size)
    }

    @Test
    fun `digitar no campo limpa so o erro daquele campo`() {
        val vm = novo()
        vm.aoSalvar()

        vm.aoMudarNome("S")

        assertNull(vm.uiState.value.erroNome)
        assertNotNull(vm.uiState.value.erroArea)
    }

    @Test
    fun `aceita virgula como separador decimal`() = runTest {
        val vm = novo()
        vm.aoMudarNome("Chácara Três Irmãos")
        vm.aoMudarArea("12,5")
        vm.aoMudarLatitude("-21,70")
        vm.aoMudarLongitude("-46,90")
        vm.aoSalvar()

        assertTrue(vm.uiState.value.salvo)
        val gravada = propriedades.observarTodas().first().first { it.nome == "Chácara Três Irmãos" }
        assertEquals(12.5, gravada.areaHectares, 0.0001)
        assertEquals(-21.70, gravada.latitude, 0.0001)
    }

    @Test
    fun `cultura vazia vira Nao informada`() = runTest {
        val vm = novo()
        vm.aoMudarNome("Sítio Novo")
        vm.aoMudarArea("3")
        vm.aoMudarLatitude("-21")
        vm.aoMudarLongitude("-46")
        vm.aoSalvar()

        val gravada = propriedades.observarTodas().first().first { it.nome == "Sítio Novo" }
        assertEquals("Não informada", gravada.cultura)
    }

    @Test
    fun `coordenada fora do planeta e recusada`() {
        val vm = novo()
        vm.aoMudarNome("Lugar Nenhum")
        vm.aoMudarArea("1")
        vm.aoMudarLatitude("95")
        vm.aoMudarLongitude("-46")
        vm.aoSalvar()

        assertNotNull(vm.uiState.value.erroCoordenadas)
        assertFalse(vm.uiState.value.salvo)
    }

    @Test
    fun `edicao carrega os dados e mantem o mesmo id ao salvar`() = runTest {
        val vm = editando(DadosDeExemplo.ID_SITIO)
        assertEquals("Sítio Boa Vista", vm.uiState.value.nome)
        // Área inteira volta sem casa decimal ("18", não "18,0").
        assertEquals("18", vm.uiState.value.area)
        assertTrue(vm.uiState.value.ehEdicao)

        vm.aoMudarNome("Sítio Boa Vista II")
        vm.aoSalvar()

        val todas = propriedades.observarTodas().first()
        assertEquals("editar não pode criar uma cópia", 2, todas.size)
        assertEquals("Sítio Boa Vista II", todas.first { it.id == DadosDeExemplo.ID_SITIO }.nome)
    }

    @Test
    fun `RN02 - excluir propriedade leva junto os lancamentos dela`() = runTest {
        val vm = editando(DadosDeExemplo.ID_SITIO)
        vm.aoExcluir()

        assertTrue(vm.uiState.value.excluido)
        assertNull(propriedades.buscarPorId(DadosDeExemplo.ID_SITIO))
        val restantes = lancamentos.observarTodos().first()
        assertTrue(restantes.none { it.propriedadeId == DadosDeExemplo.ID_SITIO })
        assertEquals("os da fazenda continuam", 6, restantes.size)
    }

    @Test
    fun `desfazer a exclusao traz a propriedade e todos os lancamentos dela`() = runTest {
        val desfazer = Desfazer()
        val antes = lancamentos.observarTodos().first().filter { it.propriedadeId == DadosDeExemplo.ID_SITIO }
        val propriedadeAntes = propriedades.buscarPorId(DadosDeExemplo.ID_SITIO)
        val vm = PropriedadeFormViewModel(propriedades, propriedadeId = DadosDeExemplo.ID_SITIO, desfazer = desfazer, selecao = selecao)

        vm.aoExcluir()
        val oferta = checkNotNull(desfazer.pendente.value)
        assertEquals("Propriedade excluída", oferta.mensagem)

        desfazer.desfazer(oferta)

        assertEquals(propriedadeAntes, propriedades.buscarPorId(DadosDeExemplo.ID_SITIO))
        val depois = lancamentos.observarTodos().first().filter { it.propriedadeId == DadosDeExemplo.ID_SITIO }
        assertEquals(antes.toSet(), depois.toSet())
        assertEquals(3, depois.size)
    }

    @Test
    fun `usar minha localizacao preenche as coordenadas com virgula`() = runTest {
        val vm = novo()
        vm.aoMudarNome("Sítio Teste")
        vm.aoSalvar() // Gera o erro de coordenadas que a localização deve limpar.

        vm.aoUsarLocalizacao(latitude = -21.73694049, longitude = -46.9316)

        assertEquals("-21,736940", vm.uiState.value.latitude)
        assertEquals("-46,931600", vm.uiState.value.longitude)
        assertNull(vm.uiState.value.erroCoordenadas)
    }

    /** Segura `salvar` até [portao] ser completado, simulando gravação lenta. */
    private class PropriedadesLentas(private val real: PropriedadeRepositoryMemoria) : PropriedadeRepository by real {
        val portao = CompletableDeferred<Unit>()
        override suspend fun salvar(propriedade: Propriedade) {
            portao.await()
            real.salvar(propriedade)
        }
    }

    @Test
    fun `dois toques em salvar criam uma propriedade so`() = runTest {
        val lentas = PropriedadesLentas(propriedades)
        val vm = PropriedadeFormViewModel(lentas, propriedadeId = null, selecao = selecao)
        preencher(vm)

        vm.aoSalvar()
        assertTrue("o botão desliga enquanto grava", vm.uiState.value.salvando)
        vm.aoSalvar()

        lentas.portao.complete(Unit)
        assertTrue(vm.uiState.value.salvo)
        assertEquals(3, propriedades.observarTodas().first().size)
    }

    @Test
    fun `area NaN ou Infinity e recusada no campo em vez de derrubar o app`() = runTest {
        for (texto in listOf("NaN", "Infinity", "-Infinity")) {
            val vm = novo()
            preencher(vm, area = texto)
            vm.aoSalvar()

            assertNotNull("área $texto", vm.uiState.value.erroArea)
            assertFalse(vm.uiState.value.salvo)
        }
        assertEquals(2, propriedades.observarTodas().first().size)
    }

    @Test
    fun `erro do banco ao salvar vira mensagem e libera o botao`() = runTest {
        val quebrado = object : PropriedadeRepository by propriedades {
            override suspend fun salvar(propriedade: Propriedade) = throw IllegalStateException("disco cheio")
        }
        val vm = PropriedadeFormViewModel(quebrado, propriedadeId = null, selecao = selecao)
        preencher(vm)
        vm.aoSalvar()

        val estado = vm.uiState.value
        assertFalse(estado.salvo)
        assertFalse("o botão volta a funcionar", estado.salvando)
        assertEquals("Não foi possível salvar. Tente de novo.", estado.erroGeral)
    }

    @Test
    fun `numero grande na edicao aparece por extenso e nao como 1E7`() = runTest {
        propriedades.salvar(
            Propriedade(
                id = "grande",
                nome = "Latifúndio",
                cultura = "Soja",
                areaHectares = 12_000_000.0,
                latitude = -0.0001,
                longitude = -46.5
            )
        )
        val vm = editando("grande")

        assertEquals("12000000", vm.uiState.value.area)
        assertEquals("-0,0001", vm.uiState.value.latitude)
        assertEquals("-46,5", vm.uiState.value.longitude)
    }

    private suspend fun gravada(nome: String = "Sítio Novo") = propriedades.observarTodas().first().first { it.nome == nome }

    @Test
    fun `area com ponto de milhar vale mil hectares, nao um`() = runTest {
        val vm = novo()
        preencher(vm, area = "1.000")
        vm.aoSalvar()

        assertTrue(vm.uiState.value.salvo)
        assertEquals(1_000.0, gravada().areaHectares, 0.0001)
    }

    @Test
    fun `area no formato brasileiro com milhar e decimais`() = runTest {
        for ((texto, esperado) in listOf("1.250,5" to 1_250.5, "12.345.678,25" to 12_345_678.25, "1250,5" to 1_250.5)) {
            val vm = novo()
            preencher(vm, area = texto)
            vm.aoSalvar()

            assertTrue(texto, vm.uiState.value.salvo)
            assertEquals(texto, esperado, propriedades.observarTodas().first().last().areaHectares, 0.0001)
        }
    }

    @Test
    fun `area com ponto decimal que nao pode ser milhar continua aceita`() = runTest {
        val vm = novo()
        preencher(vm, area = "12.5")
        vm.aoSalvar()

        assertTrue(vm.uiState.value.salvo)
        assertEquals(12.5, gravada().areaHectares, 0.0001)
    }

    @Test
    fun `area em formato ambiguo e recusada com mensagem clara`() = runTest {
        for (texto in listOf("1,000.5", "1000.000", "0.500", "1.000.5", "1,5,0", "1.00,5")) {
            val vm = novo()
            preencher(vm, area = texto)
            vm.aoSalvar()

            assertEquals(
                texto,
                "Não deu para entender a área. Use ponto só no milhar e vírgula nos decimais, como 1.250,5.",
                vm.uiState.value.erroArea
            )
            assertFalse(vm.uiState.value.salvo)
        }
        assertEquals(2, propriedades.observarTodas().first().size)
    }

    @Test
    fun `campo de area mostra como o numero foi entendido`() {
        val vm = novo()
        vm.aoMudarArea("1.000")
        assertEquals("= 1.000 ha", vm.uiState.value.leituraDaArea)
        vm.aoMudarArea("1,000")
        assertEquals("= 1 ha", vm.uiState.value.leituraDaArea)
        vm.aoMudarArea("12,5")
        assertEquals("= 12,5 ha", vm.uiState.value.leituraDaArea)
        vm.aoMudarArea("1,000.5")
        assertNull(vm.uiState.value.leituraDaArea)
        vm.aoMudarArea("")
        assertNull(vm.uiState.value.leituraDaArea)
    }

    @Test
    fun `latitude e longitude continuam aceitando ponto decimal`() = runTest {
        val vm = novo()
        vm.aoMudarNome("Sítio Novo")
        vm.aoMudarArea("3")
        vm.aoMudarLatitude("-21.7333")
        vm.aoMudarLongitude("-46.9667")
        vm.aoSalvar()

        assertTrue(vm.uiState.value.salvo)
        assertEquals(-21.7333, gravada().latitude, 0.00001)
        assertEquals(-46.9667, gravada().longitude, 0.00001)
    }

    @Test
    fun `botao de sinal poe e tira o menos da coordenada`() {
        val vm = novo()
        vm.aoMudarLatitude("21,73")
        vm.aoInverterSinalLatitude()
        assertEquals("-21,73", vm.uiState.value.latitude)
        vm.aoInverterSinalLatitude()
        assertEquals("21,73", vm.uiState.value.latitude)

        vm.aoInverterSinalLongitude()
        assertEquals("campo vazio recebe o sinal para digitar depois", "-", vm.uiState.value.longitude)
    }

    @Test
    fun `limites do Brasil incluem as pontas e as ilhas, e nao o outro hemisferio`() {
        val noBrasil = listOf(
            -33.75 to -53.4, // Chuí
            5.27 to -60.2, // Monte Caburaí
            -7.5 to -73.9, // Serra do Divisor
            -7.15 to -34.8, // Ponta do Seixas
            -3.85 to -32.4, // Fernando de Noronha
            -21.7333 to -46.9667
        )
        noBrasil.forEach { (lat, lon) -> assertTrue("$lat, $lon", PropriedadeFormViewModel.ficaNoBrasil(lat, lon)) }

        val fora = listOf(21.7333 to 46.9667, -21.7333 to 46.9667, 21.7333 to -46.9667, 38.7 to -9.1)
        fora.forEach { (lat, lon) -> assertFalse("$lat, $lon", PropriedadeFormViewModel.ficaNoBrasil(lat, lon)) }
    }

    @Test
    fun `coordenada sem o sinal de menos avisa antes de salvar e o segundo toque confirma`() = runTest {
        val vm = novo()
        vm.aoMudarNome("Sítio Novo")
        vm.aoMudarArea("3")
        vm.aoMudarLatitude("21,73")
        vm.aoMudarLongitude("46,93")

        vm.aoSalvar()
        assertNotNull(vm.uiState.value.avisoCoordenadas)
        assertFalse(vm.uiState.value.salvo)
        assertEquals(2, propriedades.observarTodas().first().size)

        vm.aoSalvar()
        assertTrue("é aviso, não bloqueio: pode haver sítio do outro lado da fronteira", vm.uiState.value.salvo)
        assertEquals(21.73, gravada().latitude, 0.0001)
    }

    @Test
    fun `corrigir a coordenada tira o aviso e coordenada do Brasil salva direto`() = runTest {
        val vm = novo()
        vm.aoMudarNome("Sítio Novo")
        vm.aoMudarArea("3")
        vm.aoMudarLatitude("21,73")
        vm.aoMudarLongitude("46,93")
        vm.aoSalvar()

        vm.aoInverterSinalLatitude()
        vm.aoInverterSinalLongitude()
        assertNull(vm.uiState.value.avisoCoordenadas)

        vm.aoSalvar()
        assertTrue(vm.uiState.value.salvo)
        assertEquals(-46.93, gravada().longitude, 0.0001)
    }

    @Test
    fun `coordenada do GPS fora do Brasil nao pede confirmacao`() = runTest {
        val vm = novo()
        vm.aoMudarNome("Sítio Novo")
        vm.aoMudarArea("3")
        vm.aoUsarLocalizacao(latitude = -25.3, longitude = -57.6) // Assunção, Paraguai.

        vm.aoSalvar()
        assertTrue(vm.uiState.value.salvo)
    }

    @Test
    fun `editar propriedade ja salva fora do Brasil nao avisa de novo`() = runTest {
        propriedades.salvar(
            Propriedade(id = "paraguai", nome = "Estância", cultura = "Soja", areaHectares = 100.0, latitude = -25.3, longitude = -57.6)
        )
        val vm = editando("paraguai")
        vm.aoMudarNome("Estância Nova")

        vm.aoSalvar()
        assertTrue(vm.uiState.value.salvo)
    }

    @Test
    fun `o que foi digitado volta depois que o Android mata o processo`() {
        val guardado = SavedStateHandle()
        val antes = PropriedadeFormViewModel(propriedades, propriedadeId = null, estadoSalvo = guardado, selecao = selecao)
        antes.aoMudarNome("Chácara Três Irmãos")
        antes.aoMudarCultura("Café")
        antes.aoMudarArea("1.250,5")
        antes.aoMudarLatitude("21,7")
        antes.aoInverterSinalLatitude()
        antes.aoMudarLongitude("-46,9")

        // O handle vai para um Bundle: só tipos simples atravessam a morte do processo.
        assertTrue(guardado.keys().all { guardado.get<Any?>(it) is String })
        val restaurado = SavedStateHandle(guardado.keys().associateWith { guardado.get<Any?>(it) })
        val depois = PropriedadeFormViewModel(propriedades, propriedadeId = null, estadoSalvo = restaurado, selecao = selecao)

        val estado = depois.uiState.value
        assertEquals("Chácara Três Irmãos", estado.nome)
        assertEquals("Café", estado.cultura)
        assertEquals("1.250,5", estado.area)
        assertEquals("-21,7", estado.latitude)
        assertEquals("-46,9", estado.longitude)
    }

    @Test
    fun `coordenadas do GPS tambem sobrevivem a morte do processo`() {
        val guardado = SavedStateHandle()
        PropriedadeFormViewModel(propriedades, propriedadeId = null, estadoSalvo = guardado, selecao = selecao)
            .aoUsarLocalizacao(latitude = -21.7, longitude = -46.9)

        val depois = PropriedadeFormViewModel(propriedades, propriedadeId = null, estadoSalvo = guardado, selecao = selecao)

        assertEquals("-21,700000", depois.uiState.value.latitude)
        assertEquals("-46,900000", depois.uiState.value.longitude)
    }

    @Test
    fun `na edicao o valor restaurado vale mais que o do banco`() {
        val restaurado = SavedStateHandle(mapOf(PropriedadeFormViewModel.CHAVE_NOME to "Sítio Boa Vista II"))
        val vm =
            PropriedadeFormViewModel(propriedades, propriedadeId = DadosDeExemplo.ID_SITIO, estadoSalvo = restaurado, selecao = selecao)

        assertEquals("Sítio Boa Vista II", vm.uiState.value.nome)
        assertEquals("o que não foi guardado vem do banco", "18", vm.uiState.value.area)
        assertTrue(vm.uiState.value.ehEdicao)
    }
}
