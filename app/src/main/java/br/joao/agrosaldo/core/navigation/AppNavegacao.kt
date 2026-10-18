package br.joao.agrosaldo.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.joao.agrosaldo.core.seguranca.acharFragmentActivity
import br.joao.agrosaldo.core.ui.LocalValoresOcultos
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.feature.conta.OfertaDeBloqueioRoute
import br.joao.agrosaldo.feature.conta.TelaBloqueioRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pedido de fora do app (toque na notificação de chuva) para abrir o Clima, com a
 * propriedade do aviso quando houver. Igualdade por identidade: cada toque é um pedido
 * novo, mesmo que repita a propriedade.
 */
class PedidoDoClima(val propriedadeId: String?)

/** Chave do estado das telas guardado enquanto o app está bloqueado. */
private const val CHAVE_CONTEUDO = "conteudo"

/**
 * Raiz da UI: Scaffold (barras superior/inferior), NavHost, snackbar de desfazer e,
 * no lugar de tudo isso, a tela de bloqueio.
 *
 * Único ponto que conhece o NavController: as telas expõem callbacks, o que as mantém
 * desacopladas do grafo e compatíveis com @Preview. Mudanças de sessão (login, logout,
 * novo aceite) são observadas em [SessaoViewModel] e convertidas em navegação aqui.
 *
 * @param pedidoDoClima último toque na notificação de chuva; cada pedido novo abre a aba Clima.
 * @param desfazer oferta de desfazer exclusão, a mesma instância usada pelos formulários.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavegacao(
    desfazer: Desfazer,
    modifier: Modifier = Modifier,
    pedidoDoClima: PedidoDoClima? = null,
    navController: NavHostController = rememberNavController(),
    sessaoViewModel: SessaoViewModel = viewModel(factory = SessaoViewModel.Factory),
    privacidadeViewModel: PrivacidadeViewModel = viewModel(factory = PrivacidadeViewModel.Factory)
) {
    val valoresOcultos by privacidadeViewModel.valoresOcultos.collectAsStateWithLifecycle()
    val logado by sessaoViewModel.logado.collectAsStateWithLifecycle()
    val precisaNovoAceite by sessaoViewModel.precisaNovoAceite.collectAsStateWithLifecycle()
    val precisaDesbloquear by sessaoViewModel.precisaDesbloquear.collectAsStateWithLifecycle()
    val oferecerBloqueio by sessaoViewModel.oferecerBloqueio.collectAsStateWithLifecycle()

    // Calculado uma única vez: alterar startDestination de um NavHost já montado não tem efeito.
    // Mudanças posteriores de sessão são tratadas pelo LaunchedEffect abaixo.
    val destinoInicial = remember { if (logado) Rotas.INICIO else Rotas.LOGIN }

    val entradaAtual by navController.currentBackStackEntryAsState()
    val rotaAtual = entradaAtual?.destination?.route
    val navegador = remember(navController) { Navegador(navController) }

    // Redireciona conforme a sessão. popUpTo(graph.id, inclusive) limpa a back stack para
    // que "voltar" não cruze a fronteira login/área autenticada nem pule o novo aceite.
    // A rota atual é chave para que nenhuma navegação (ex.: pedido externo) escape das regras.
    LaunchedEffect(logado, precisaNovoAceite, rotaAtual) {
        val rota = navController.currentDestination?.route ?: return@LaunchedEffect
        val anterior = navController.previousBackStackEntry?.destination?.route
        val destino = RegrasDeNavegacao.destino(rota, anterior, logado, precisaNovoAceite)
        if (destino != null) {
            navController.navigate(destino) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Mede o tempo em segundo plano para o rebloqueio. Usa ON_STOP/ON_START e não ON_PAUSE,
    // pois diálogos do sistema (incluindo o BiometricPrompt) disparam ON_PAUSE.
    val ciclo = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_STOP -> sessaoViewModel.aoIrParaSegundoPlano()
                Lifecycle.Event.ON_START -> sessaoViewModel.aoVoltarParaPrimeiroPlano()
                else -> Unit
            }
        }
        ciclo.addObserver(observador)
        onDispose { ciclo.removeObserver(observador) }
    }

    // Toque na notificação de chuva: seleciona a propriedade do aviso e abre a aba Clima, só com
    // sessão e sem novo aceite pendente (ver SessaoViewModel.prepararAberturaDoClima).
    LaunchedEffect(pedidoDoClima) {
        val pedido = pedidoDoClima ?: return@LaunchedEffect
        // Aguarda o NavHost ter a primeira entrada na back stack.
        navController.currentBackStackEntryFlow.first()
        if (sessaoViewModel.prepararAberturaDoClima(pedido.propriedadeId)) navegador.irParaAba(Rotas.CLIMA)
    }

    val contexto = LocalContext.current

    // Snackbar de "Desfazer" exclusão. Fica na raiz porque a tela que excluiu já foi fechada.
    // Se a exclusão pendente mudar, o LaunchedEffect reinicia e descarta a snackbar anterior.
    val estadoDaBarra = remember { SnackbarHostState() }
    // Escopo fora do LaunchedEffect: a mensagem de erro precisa sobreviver ao reinício do
    // efeito, que ocorre assim que a exclusão deixa de estar pendente.
    val escopoDaBarra = rememberCoroutineScope()
    val exclusaoPendente by desfazer.pendente.collectAsStateWithLifecycle()
    LaunchedEffect(exclusaoPendente) {
        val exclusao = exclusaoPendente ?: return@LaunchedEffect
        // Oferta expirada (ex.: app reaberto muito depois) é descartada sem exibir.
        if (desfazer.expirou(exclusao)) {
            desfazer.dispensar(exclusao)
            return@LaunchedEffect
        }
        val resultado = estadoDaBarra.showSnackbar(
            message = exclusao.mensagem,
            actionLabel = "Desfazer",
            withDismissAction = true,
            duration = SnackbarDuration.Long
        )
        if (resultado == SnackbarResult.ActionPerformed) {
            if (!desfazer.desfazer(exclusao)) {
                escopoDaBarra.launch { estadoDaBarra.showSnackbar("Não foi possível desfazer.") }
            }
        } else {
            desfazer.dispensar(exclusao)
        }
    }

    // Conta ainda sendo lida ou desvio pendente: a tela atual fica coberta até a navegação decidir.
    val telaPodeAparecer = RegrasDeNavegacao.telaPodeAparecer(
        rota = rotaAtual,
        anterior = navController.previousBackStackEntry?.destination?.route,
        logado = logado,
        precisaNovoAceite = precisaNovoAceite
    )

    val ehAbaPrincipal = rotaAtual in Rotas.ABAS
    val cabecalhoEscuro = rotaAtual in Rotas.COM_CABECALHO_ESCURO

    // Ícones da status bar: claros sobre cabeçalho escuro ou em modo escuro; escuros caso contrário.
    val modoEscuro = isSystemInDarkTheme()
    val vista = LocalView.current
    if (!vista.isInEditMode) {
        SideEffect {
            contexto.acharFragmentActivity()?.window?.let { janela ->
                // A tela de bloqueio e a capa têm fundo claro e ficam no lugar de qualquer rota.
                WindowCompat.getInsetsController(janela, vista).isAppearanceLightStatusBars =
                    !modoEscuro &&
                    (precisaDesbloquear || !telaPodeAparecer || !cabecalhoEscuro)
            }
        }
    }

    val titulo = when (rotaAtual) {
        Rotas.CRIAR_CONTA -> "Criar conta"
        Rotas.CONTA -> "Conta"
        Rotas.ALTERAR_SENHA -> "Alterar senha"
        Rotas.AJUSTES_AVISO -> "Aviso de chuva"
        Rotas.POLITICA -> "Privacidade e dados"
        Rotas.HISTORICO -> "Histórico"
        Rotas.FORM_PROPRIEDADE ->
            if (entradaAtual?.arguments?.getString(Rotas.ARG_ID) == null) {
                "Nova propriedade"
            } else {
                "Editar propriedade"
            }
        // Sem TopAppBar: abas e formulário de lançamento têm cabeçalho próprio; login e
        // consentimento exibem a marca.
        else -> null
    }

    // Bloqueado, o conteúdo sai da composição: diálogos e menus abrem em janelas próprias,
    // acima de qualquer capa na janela do app, e o teclado alcançaria os campos debaixo
    // dela. Fora da composição não há diálogo, menu nem foco. O estado salvável das telas
    // (inclusive diálogos abertos) fica guardado aqui e volta ao desbloquear; os
    // ViewModels ficam nas entradas da back stack. A tela de bloqueio, com o prompt
    // biométrico, segue na janela do app como antes.
    val estadoDoConteudo = rememberSaveableStateHolder()

    // Ocultação de valores é global; telas com valores leem LocalValoresOcultos.
    CompositionLocalProvider(LocalValoresOcultos provides valoresOcultos) {
        if (precisaDesbloquear) {
            // Com o app bloqueado, "voltar" envia a task para segundo plano.
            BackHandler { contexto.acharFragmentActivity()?.moveTaskToBack(true) }
            TelaBloqueioRoute(
                modifier = modifier,
                aoDesbloquear = sessaoViewModel::aoDesbloquear,
                aoUsarSenha = {
                    // Sai da conta: o estado guardado das telas (ex.: diálogo aberto) era dela.
                    estadoDoConteudo.removeState(CHAVE_CONTEUDO)
                    sessaoViewModel.aoUsarSenha()
                }
            )
        } else {
            estadoDoConteudo.SaveableStateProvider(CHAVE_CONTEUDO) {
                Box(modifier = modifier.fillMaxSize()) {
                    Scaffold(
                        // Coberto, o conteúdo segue composto, mas oculto também da acessibilidade.
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (!telaPodeAparecer) Modifier.clearAndSetSemantics { } else Modifier),
                        snackbarHost = {
                            SnackbarHost(
                                hostState = estadoDaBarra,
                                // No formulário (sem bottomBar), afasta a snackbar do botão de salvar.
                                modifier = if (rotaAtual == Rotas.FORM_LANCAMENTO) {
                                    Modifier
                                        .navigationBarsPadding()
                                        .padding(bottom = 84.dp)
                                } else {
                                    Modifier
                                }
                            )
                        },
                        // Com cabeçalho escuro, o inset superior fica a cargo do cabeçalho; os laterais
                        // (paisagem, display cutout) continuam aplicados.
                        contentWindowInsets = if (cabecalhoEscuro) {
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                        } else {
                            ScaffoldDefaults.contentWindowInsets
                        },
                        topBar = {
                            if (titulo != null) {
                                TopAppBar(
                                    title = { Text(titulo, style = MaterialTheme.typography.titleLarge) },
                                    navigationIcon = {
                                        if (!ehAbaPrincipal) {
                                            IconButton(onClick = { navController.popBackStack() }) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Voltar"
                                                )
                                            }
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                    )
                                )
                            }
                        },
                        bottomBar = {
                            // Barra inferior só nas abas principais.
                            if (ehAbaPrincipal) {
                                BarraInferior(
                                    rotaAtual = entradaAtual?.destination,
                                    aoSelecionar = navegador::irParaAba,
                                    aoNovoLancamento = { navegador.abrirFormularioDeLancamento(Rotas.formLancamento()) }
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.background
                    ) { espacoInterno ->

                        NavHost(
                            navController = navController,
                            startDestination = destinoInicial,
                            modifier = Modifier.padding(espacoInterno),
                            enterTransition = { entrada() },
                            exitTransition = { saida() },
                            popEnterTransition = { entradaAoVoltar() },
                            popExitTransition = { saidaAoVoltar() }
                        ) {
                            grafoDaConta(navegador)
                            grafoDoFinanceiro(navegador, aoAlternarValores = privacidadeViewModel::aoAlternar)
                            grafoDoClima(navegador)
                        }
                    }

                    // Surface opaca também consome os toques destinados à tela coberta.
                    if (!telaPodeAparecer) {
                        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {}
                    }
                }
            }

            // Conta recém-criada: oferece ligar o bloqueio por digital assim que uma aba aparece.
            if (oferecerBloqueio && telaPodeAparecer && ehAbaPrincipal) {
                OfertaDeBloqueioRoute(
                    aoLigar = sessaoViewModel::aoLigarBloqueioPelaOferta,
                    aoDispensar = sessaoViewModel::aoDispensarOfertaDeBloqueio
                )
            }
        }
    }
}
