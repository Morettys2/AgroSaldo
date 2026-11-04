package br.joao.agrosaldo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import br.joao.agrosaldo.core.ui.PermissaoNotificacao
import br.joao.agrosaldo.core.util.emReal
import br.joao.agrosaldo.core.util.formatarBr
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.relogioEm
import br.joao.agrosaldo.data.repository.LancamentoRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.domain.RelogioMonotonicoIndisponivel
import br.joao.agrosaldo.feature.clima.ClimaScreen
import br.joao.agrosaldo.feature.clima.ClimaUiState
import br.joao.agrosaldo.feature.inicio.InicioScreen
import br.joao.agrosaldo.feature.inicio.InicioUiState
import br.joao.agrosaldo.feature.lancamento.FiltroLancamento
import br.joao.agrosaldo.feature.lancamento.LancamentoFormAcoes
import br.joao.agrosaldo.feature.lancamento.LancamentoFormRoute
import br.joao.agrosaldo.feature.lancamento.LancamentoFormScreen
import br.joao.agrosaldo.feature.lancamento.LancamentoFormUiState
import br.joao.agrosaldo.feature.lancamento.LancamentoFormViewModel
import br.joao.agrosaldo.feature.lancamento.LancamentoListaScreen
import br.joao.agrosaldo.feature.lancamento.LancamentoListaUiState
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Testes de interface: a tela de verdade, desenhada no JVM pelo Robolectric, recebe
 * toques e confere o que aparece. Complementam os testes de ViewModel, que testam a
 * regra mas não o que a pessoa vê nem o que acontece ao tocar.
 */
@RunWith(RobolectricTestRunner::class)
// Tela de celular comum (a do protótipo no Figma): numa tela pequena, o que fica fora da
// área visível de uma lista não é desenhado e o teste não acharia.
@Config(qualifiers = "w412dp-h917dp-xxhdpi")
class TelasTest {

    @get:Rule
    val compose = createComposeRule()

    private val hoje = LocalDate.of(2026, 10, 14)
    private val propriedades = DadosDeExemplo.propriedades()

    @Test
    fun `teclado numerico digita o valor em centavos, como numa calculadora`() {
        val viewModel = LancamentoFormViewModel(
            lancamentoRepository = LancamentoRepositoryMemoria(emptyList()),
            propriedadeRepository = PropriedadeRepositoryMemoria(),
            selecao = SelecaoAtual(propriedadeInicial = DadosDeExemplo.ID_FAZENDA, relogio = relogioEm(LocalDate.of(2026, 10, 14))),
            lancamentoId = null,
            relogio = relogioEm(hoje),
            desfazer = Desfazer(RelogioMonotonicoIndisponivel),
            estadoSalvo = SavedStateHandle()
        )
        compose.setContent {
            AgroSaldoTheme(escuro = false) {
                LancamentoFormRoute(
                    lancamentoId = null,
                    aoConcluir = {},
                    aoLancarDeNovo = {},
                    aoCadastrarPropriedade = {},
                    viewModel = viewModel
                )
            }
        }

        listOf("1", "2", "5").forEach { compose.onNodeWithContentDescription(it).performClick() }

        assertEquals("125", viewModel.uiState.value.valorDigitos)
        compose.onNodeWithContentDescription("Valor: " + 125L.emReal()).assertExists()

        compose.onNodeWithContentDescription("Apagar").performClick()
        assertEquals("12", viewModel.uiState.value.valorDigitos)
    }

    @Test
    fun `botao de salvar fica desligado enquanto grava`() {
        var estado by mutableStateOf(formulario(salvando = true))
        compose.setContent {
            AgroSaldoTheme(escuro = false) {
                LancamentoFormScreen(uiState = estado, titulo = "Novo lançamento", acoes = LancamentoFormAcoes())
            }
        }

        compose.onNodeWithText("Salvar despesa").assertIsNotEnabled()

        estado = formulario(salvando = false)
        compose.onNodeWithText("Salvar despesa").assertIsEnabled()
    }

    @Test
    fun `formulario sem propriedade oferece o cadastro`() {
        var pediuCadastro = false
        compose.setContent {
            AgroSaldoTheme(escuro = false) {
                LancamentoFormScreen(
                    uiState = LancamentoFormUiState(data = hoje.formatarBr(), carregado = true, propriedades = emptyList(), hoje = hoje),
                    titulo = "Novo lançamento",
                    acoes = LancamentoFormAcoes(aoCadastrarPropriedade = { pediuCadastro = true })
                )
            }
        }

        compose.onNodeWithText("Cadastrar propriedade").performClick()

        assertTrue(pediuCadastro)
    }

    @Test
    fun `inicio sem propriedade leva ao cadastro`() {
        var pediuCadastro = false
        compose.setContent {
            AgroSaldoTheme(escuro = false) {
                InicioScreen(
                    uiState = InicioUiState(mes = YearMonth.from(hoje), carregando = false, temPropriedade = false, hoje = hoje),
                    aoVoltarMes = {}, aoAvancarMes = {}, aoSelecionarPropriedade = {},
                    aoNovoLancamento = {}, aoEditarLancamento = {}, aoAbrirClima = {}, aoAbrirHistorico = {},
                    aoAbrirLancamentos = {}, aoAbrirConta = {}, aoAlternarValores = {},
                    aoCadastrarPropriedade = { pediuCadastro = true }
                )
            }
        }

        compose.onNodeWithText("Cadastrar propriedade").performClick()

        assertTrue(pediuCadastro)
    }

    @Test
    fun `erro na primeira leitura aparece no inicio no lugar do convite ao cadastro`() {
        compose.setContent {
            AgroSaldoTheme(escuro = false) {
                InicioScreen(
                    uiState = InicioUiState(
                        mes = YearMonth.from(hoje),
                        carregando = false,
                        temPropriedade = false,
                        hoje = hoje,
                        erro = "Deu ruim."
                    ),
                    aoVoltarMes = {}, aoAvancarMes = {}, aoSelecionarPropriedade = {},
                    aoNovoLancamento = {}, aoEditarLancamento = {}, aoAbrirClima = {}, aoAbrirHistorico = {},
                    aoAbrirLancamentos = {}, aoAbrirConta = {}, aoAlternarValores = {}, aoCadastrarPropriedade = {}
                )
            }
        }

        compose.onNodeWithText("Deu ruim.").assertExists()
        compose.onNodeWithText("Cadastrar propriedade").assertDoesNotExist()
    }

    @Test
    fun `erro na primeira leitura aparece no clima no lugar do convite ao cadastro`() {
        compose.setContent {
            AgroSaldoTheme(escuro = false) {
                ClimaScreen(
                    uiState = ClimaUiState(carregando = false, temPropriedade = false, erro = "Deu ruim."),
                    permissao = PermissaoNotificacao(concedida = true, pedir = {}),
                    aoSelecionarPropriedade = {},
                    aoAtualizar = {},
                    aoAbrirAjustes = {},
                    aoCadastrarPropriedade = {}
                )
            }
        }

        compose.onNodeWithText("Deu ruim.").assertExists()
        compose.onNodeWithText("Cadastrar propriedade").assertDoesNotExist()
    }

    @Test
    fun `lista vazia por causa do filtro oferece limpar os filtros`() {
        var limpou = false
        compose.setContent {
            AgroSaldoTheme(escuro = false) {
                LancamentoListaScreen(
                    uiState = LancamentoListaUiState(
                        mes = YearMonth.from(hoje),
                        carregando = false,
                        temPropriedade = true,
                        nomePropriedade = propriedades.first().nome,
                        propriedades = propriedades,
                        filtro = FiltroLancamento.DESPESAS
                    ),
                    aoSelecionarMes = {}, aoMudarFiltro = {}, aoMudarCategoria = {},
                    aoLimparFiltros = { limpou = true },
                    aoBuscar = {}, aoFecharBusca = {}, aoSelecionarPropriedade = {}, aoEditarLancamento = {}
                )
            }
        }

        compose.onNodeWithText("Nenhum lançamento com esse filtro neste mês.").assertExists()
        compose.onNodeWithText("Limpar filtros").performClick()

        assertTrue(limpou)
    }

    private fun formulario(salvando: Boolean) = LancamentoFormUiState(
        data = hoje.formatarBr(),
        carregado = true,
        propriedades = propriedades,
        propriedadeId = propriedades.first().id,
        valorDigitos = "5000",
        salvando = salvando,
        hoje = hoje
    )
}
