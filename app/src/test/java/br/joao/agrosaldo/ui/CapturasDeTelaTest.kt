package br.joao.agrosaldo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import br.joao.agrosaldo.clima.ApoioClima
import br.joao.agrosaldo.core.ui.PermissaoNotificacao
import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.preferencias.AjustesAvisoMemoria
import br.joao.agrosaldo.data.repository.ClimaRepositoryMemoria
import br.joao.agrosaldo.data.repository.LancamentoRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.domain.clima.CondicaoTempo
import br.joao.agrosaldo.feature.clima.ClimaScreen
import br.joao.agrosaldo.feature.clima.ClimaUiState
import br.joao.agrosaldo.feature.clima.DiaUi
import br.joao.agrosaldo.feature.conta.LoginScreen
import br.joao.agrosaldo.feature.conta.LoginUiState
import br.joao.agrosaldo.feature.historico.HistoricoRoute
import br.joao.agrosaldo.feature.historico.HistoricoViewModel
import br.joao.agrosaldo.feature.inicio.InicioRoute
import br.joao.agrosaldo.feature.inicio.InicioViewModel
import br.joao.agrosaldo.feature.lancamento.LancamentoFormRoute
import br.joao.agrosaldo.feature.lancamento.LancamentoFormViewModel
import br.joao.agrosaldo.feature.lancamento.LancamentoListaRoute
import br.joao.agrosaldo.feature.lancamento.LancamentoListaViewModel
import br.joao.agrosaldo.feature.propriedade.PropriedadeListaRoute
import br.joao.agrosaldo.feature.propriedade.PropriedadeListaViewModel
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Capturas das telas principais, desenhadas no JVM (Robolectric + Roborazzi), com os
 * ViewModels de verdade sobre os dados de exemplo dos testes.
 *
 * Servem para o README e para revisar o visual de um PR sem abrir o emulador: o CI grava
 * as imagens em `app/build/capturas`. Não comparam com uma imagem anterior (isso quebraria
 * a cada ajuste de pixel); só garantem que a tela desenha sem erro.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h917dp-xxhdpi")
class CapturasDeTelaTest {

    @get:Rule
    val compose = createComposeRule()

    /** 01/10/2026 14:40 em Brasília: o mesmo "agora" dos testes de clima. */
    private val hoje = LocalDate.of(2026, 10, 1)
    private val agoraMs = ApoioClima.AGORA.toEpochMilli()

    private val selecao = SelecaoAtual(propriedadeInicial = DadosDeExemplo.ID_FAZENDA, mesInicial = YearMonth.from(hoje))
    private val propriedades = PropriedadeRepositoryMemoria()
    private val lancamentos = LancamentoRepositoryMemoria(DadosDeExemplo.lancamentos(hoje))
    private val clima = ClimaRepositoryMemoria(
        mapOf(DadosDeExemplo.ID_FAZENDA to ApoioClima.previsao(mapOf(18 to (85 to 6.0))))
    )

    private fun capturar(nome: String, escuro: Boolean = false, escalaDaFonte: Float = 1f, tela: @Composable () -> Unit) {
        compose.setContent {
            val densidade = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densidade.density, escalaDaFonte)) {
                AgroSaldoTheme(escuro = escuro) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { tela() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/capturas/$nome.png")
    }

    private fun viewModelDoInicio() = InicioViewModel(
        lancamentoRepository = lancamentos,
        propriedadeRepository = propriedades,
        clima = clima,
        ajustes = AjustesAvisoMemoria(),
        selecao = selecao,
        relogio = { agoraMs },
        nomeDoUsuario = flowOf("Produtor Exemplo"),
        hoje = { hoje }
    )

    @Composable
    private fun TelaInicio(viewModel: InicioViewModel) {
        InicioRoute(
            aoNovoLancamento = {}, aoEditarLancamento = {}, aoAbrirClima = {}, aoAbrirHistorico = {},
            aoAbrirLancamentos = {}, aoAbrirConta = {}, aoAlternarValores = {}, aoCadastrarPropriedade = {},
            viewModel = viewModel
        )
    }

    @Test
    fun inicio() {
        val viewModel = viewModelDoInicio()
        capturar("01-inicio") { TelaInicio(viewModel) }
    }

    @Test
    fun inicioEscuro() {
        val viewModel = viewModelDoInicio()
        capturar("02-inicio-escuro", escuro = true) { TelaInicio(viewModel) }
    }

    @Test
    fun lancamentos() {
        val viewModel = LancamentoListaViewModel(
            lancamentoRepository = lancamentos,
            propriedadeRepository = propriedades,
            selecao = selecao,
            hoje = { hoje },
            processamento = EmptyCoroutineContext
        )
        capturar("03-lancamentos") { LancamentoListaRoute(aoEditarLancamento = {}, viewModel = viewModel) }
    }

    private fun formulario() = LancamentoFormViewModel(
        lancamentoRepository = lancamentos,
        propriedadeRepository = propriedades,
        selecao = selecao,
        lancamentoId = null,
        hoje = { hoje }
    ).apply {
        aoTeclar("3")
        aoTeclar("2")
        aoTeclar("0")
        aoTeclar("00")
        aoMudarDescricao("Abastecimento do trator")
    }

    @Test
    fun novoLancamento() {
        val viewModel = formulario()
        capturar("04-novo-lancamento") {
            LancamentoFormRoute(lancamentoId = null, aoConcluir = {}, aoLancarDeNovo = {}, aoCadastrarPropriedade = {}, viewModel = viewModel)
        }
    }

    @Test
    fun novoLancamentoComFonteGrande() {
        val viewModel = formulario()
        capturar("05-novo-lancamento-fonte-2x", escalaDaFonte = 2f) {
            LancamentoFormRoute(lancamentoId = null, aoConcluir = {}, aoLancarDeNovo = {}, aoCadastrarPropriedade = {}, viewModel = viewModel)
        }
    }

    @Test
    fun clima() {
        // Estado montado à mão: a tela de Clima pede a permissão de notificação pelo sistema.
        val estado = ClimaUiState(
            carregando = false,
            temPropriedade = true,
            nomePropriedade = "Fazenda Santa Clara",
            temPrevisao = true,
            temperaturaAtual = "26°",
            condicaoAtual = CondicaoTempo.NUBLADO,
            resumo = "Chuva prevista a partir das 18h",
            janelaAte = "até as 2h de amanhã",
            probabilidadeJanela = 85,
            chanceEVolume = "85% · 6 mm",
            vaiChover = true,
            recomendacaoTitulo = "Dá para adiar a irrigação do pivô",
            recomendacaoTexto = "Previsão de 85% de chance e 6 mm nas próximas 12 horas. " +
                "Passou dos limites que você configurou (60% de chance ou 2 mm).",
            dias = listOf(
                DiaUi("Hoje", CondicaoTempo.CHUVA, "31°", "22°", 85, "12 mm"),
                DiaUi("Amanhã", CondicaoTempo.PANCADAS, "29°", "21°", 65, "6 mm"),
                DiaUi("Sábado", CondicaoTempo.NUBLADO, "28°", "20°", 30, "0 mm"),
                DiaUi("Domingo", CondicaoTempo.CEU_LIMPO, "30°", "19°", 10, "0 mm"),
                DiaUi("Segunda", CondicaoTempo.CEU_LIMPO, "31°", "19°", 5, "0 mm")
            ),
            atualizadaEm = "Atualizada às 14:05"
        )
        capturar("06-clima") {
            ClimaScreen(
                uiState = estado,
                permissao = PermissaoNotificacao(concedida = true, pedir = {}),
                aoSelecionarPropriedade = {}, aoAtualizar = {}, aoAbrirAjustes = {}, aoCadastrarPropriedade = {}
            )
        }
    }

    @Test
    fun historico() {
        val viewModel = HistoricoViewModel(
            lancamentoRepository = lancamentos,
            propriedadeRepository = propriedades,
            selecao = selecao,
            hoje = { hoje }
        )
        capturar("07-historico") { HistoricoRoute(aoAbrirMes = {}, viewModel = viewModel) }
    }

    @Test
    fun sitios() {
        val viewModel = PropriedadeListaViewModel(
            propriedadeRepository = propriedades,
            lancamentoRepository = lancamentos,
            selecao = selecao
        )
        capturar("08-sitios") {
            PropriedadeListaRoute(aoAbrirNova = {}, aoAbrirPropriedade = {}, aoAbrirConta = {}, viewModel = viewModel)
        }
    }

    @Test
    fun login() {
        capturar("09-login") {
            LoginScreen(
                uiState = LoginUiState(email = "produtor@exemplo.com"),
                aoMudarEmail = {}, aoMudarSenha = {}, aoEntrar = {}, aoCriarConta = {}
            )
        }
    }

    @Test
    fun inicioVazio() {
        val viewModel = InicioViewModel(
            lancamentoRepository = LancamentoRepositoryMemoria(emptyList()),
            propriedadeRepository = PropriedadeRepositoryMemoria(emptyList()),
            clima = ClimaRepositoryMemoria(),
            ajustes = AjustesAvisoMemoria(),
            selecao = SelecaoAtual(),
            relogio = { agoraMs },
            hoje = { hoje }
        )
        capturar("10-inicio-vazio") { TelaInicio(viewModel) }
    }
}
