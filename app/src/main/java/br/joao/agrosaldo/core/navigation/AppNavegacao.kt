package br.joao.agrosaldo.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.joao.agrosaldo.core.seguranca.acharFragmentActivity
import br.joao.agrosaldo.core.ui.LocalValoresOcultos
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.feature.clima.AjustesAvisoRoute
import br.joao.agrosaldo.feature.clima.ClimaRoute
import br.joao.agrosaldo.feature.conta.AlterarSenhaRoute
import br.joao.agrosaldo.feature.conta.CadastroRoute
import br.joao.agrosaldo.feature.conta.CadastroViewModel
import br.joao.agrosaldo.feature.conta.ConsentimentoRoute
import br.joao.agrosaldo.feature.conta.ContaRoute
import br.joao.agrosaldo.feature.conta.LoginRoute
import br.joao.agrosaldo.feature.conta.NovoConsentimentoRoute
import br.joao.agrosaldo.feature.conta.OfertaDeBloqueioRoute
import br.joao.agrosaldo.feature.conta.PoliticaScreen
import br.joao.agrosaldo.feature.conta.TelaBloqueioRoute
import br.joao.agrosaldo.feature.historico.HistoricoRoute
import br.joao.agrosaldo.feature.inicio.InicioRoute
import br.joao.agrosaldo.feature.lancamento.LancamentoFormRoute
import br.joao.agrosaldo.feature.lancamento.LancamentoListaRoute
import br.joao.agrosaldo.feature.propriedade.PropriedadeFormRoute
import br.joao.agrosaldo.feature.propriedade.PropriedadeListaRoute
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro
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

private data class Aba(
    val rota: String,
    val rotulo: String,
    val icone: ImageVector
)

private val ABAS = listOf(
    Aba(Rotas.INICIO, "Início", IconesAgro.Inicio),
    Aba(Rotas.LANCAMENTOS, "Lançamentos", IconesAgro.Lista),
    Aba(Rotas.CLIMA, "Clima", IconesAgro.Nuvem),
    Aba(Rotas.PROPRIEDADES, "Sítios", IconesAgro.Sitio)
)

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
    pedidoDoClima: PedidoDoClima? = null,
    navController: NavHostController = rememberNavController(),
    sessaoViewModel: SessaoViewModel = viewModel(factory = SessaoViewModel.Factory),
    privacidadeViewModel: PrivacidadeViewModel = viewModel(factory = PrivacidadeViewModel.Factory),
    desfazer: Desfazer
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
        if (sessaoViewModel.prepararAberturaDoClima(pedido.propriedadeId)) navegarParaAba(navController, Rotas.CLIMA)
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
                    !modoEscuro && (precisaDesbloquear || !telaPodeAparecer || !cabecalhoEscuro)
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
            if (entradaAtual?.arguments?.getString(Rotas.ARG_ID) == null) "Nova propriedade"
            else "Editar propriedade"
        // Sem TopAppBar: abas e formulário de lançamento têm cabeçalho próprio; login e
        // consentimento exibem a marca.
        else -> null
    }

    // Toque duplo não empilha a mesma tela: com launchSingleTop, a segunda navegação reaproveita o topo.
    val abrir: (String) -> Unit = { rota -> navController.navigate(rota) { launchSingleTop = true } }

    // Ignora se o formulário já está no topo, evitando empilhar dois com toques rápidos.
    val abrirFormulario: (String) -> Unit = { rota ->
        if (navController.currentDestination?.route != Rotas.FORM_LANCAMENTO) navController.navigate(rota)
    }
    val abrirNovoLancamento = { abrirFormulario(Rotas.formLancamento()) }

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
                aoDesbloquear = sessaoViewModel::aoDesbloquear,
                aoUsarSenha = {
                    // Sai da conta: o estado guardado das telas (ex.: diálogo aberto) era dela.
                    estadoDoConteudo.removeState(CHAVE_CONTEUDO)
                    sessaoViewModel.aoUsarSenha()
                }
            )
        } else {
            estadoDoConteudo.SaveableStateProvider(CHAVE_CONTEUDO) {
                Box(modifier = Modifier.fillMaxSize()) {
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
                                    aoSelecionar = { rota -> navegarParaAba(navController, rota) },
                                    aoNovoLancamento = abrirNovoLancamento
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.background
                    ) { espacoInterno ->

                        NavHost(
                            navController = navController,
                            startDestination = destinoInicial,
                            modifier = Modifier.padding(espacoInterno)
                        ) {

                            // Fluxo não autenticado

                            composable(Rotas.LOGIN) {
                                LoginRoute(aoCriarConta = { abrir(Rotas.CRIAR_CONTA) })
                            }

                            composable(Rotas.CRIAR_CONTA) {
                                // Escopo da entrada CRIAR_CONTA; compartilhado com CONSENTIMENTO.
                                val cadastroViewModel: CadastroViewModel =
                                    viewModel(factory = CadastroViewModel.Factory)
                                CadastroRoute(
                                    viewModel = cadastroViewModel,
                                    aoIrParaConsentimento = { abrir(Rotas.CONSENTIMENTO) },
                                    aoVoltarParaLogin = { navController.popBackStack() }
                                )
                            }

                            composable(Rotas.CONSENTIMENTO) { entrada ->
                                // Reutiliza o CadastroViewModel da entrada CRIAR_CONTA. Precisa de remember:
                                // após criar a conta a back stack é limpa e getBackStackEntry lançaria
                                // exceção durante a animação de saída desta tela.
                                val cadastroViewModel = remember(entrada) {
                                    val donoDoCadastro = navController.getBackStackEntry(Rotas.CRIAR_CONTA)
                                    val provedor = ViewModelProvider(donoDoCadastro, CadastroViewModel.Factory)
                                    provedor[CadastroViewModel::class.java]
                                }
                                ConsentimentoRoute(
                                    viewModel = cadastroViewModel,
                                    aoLerPolitica = { abrir(Rotas.POLITICA) },
                                    // Recusa não persiste nada.
                                    aoRecusar = { navController.popBackStack(Rotas.LOGIN, inclusive = false) }
                                )
                            }

                            composable(Rotas.POLITICA) {
                                PoliticaScreen()
                            }

                            composable(Rotas.NOVO_CONSENTIMENTO) {
                                // A navegação após aceitar/recusar vem do LaunchedEffect de sessão.
                                NovoConsentimentoRoute(aoLerPolitica = { abrir(Rotas.POLITICA) })
                            }

                            // Área autenticada

                            composable(Rotas.CONTA) {
                                ContaRoute(
                                    aoAbrirPrivacidade = { abrir(Rotas.POLITICA) },
                                    aoAlterarSenha = { abrir(Rotas.ALTERAR_SENHA) },
                                    aoAbrirAvisoChuva = { abrir(Rotas.AJUSTES_AVISO) },
                                    aoAbrirPropriedades = { navegarParaAba(navController, Rotas.PROPRIEDADES) }
                                )
                            }

                            composable(Rotas.ALTERAR_SENHA) {
                                AlterarSenhaRoute(
                                    aoConcluir = {
                                        // Guarda contra pop duplo se o usuário já tiver voltado.
                                        if (navController.currentDestination?.route == Rotas.ALTERAR_SENHA) {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }

                            composable(Rotas.INICIO) {
                                InicioRoute(
                                    aoNovoLancamento = { tipo -> abrirFormulario(Rotas.formLancamento(tipo = tipo)) },
                                    aoEditarLancamento = { id -> abrirFormulario(Rotas.formLancamento(id)) },
                                    aoAbrirClima = { navegarParaAba(navController, Rotas.CLIMA) },
                                    aoAbrirHistorico = { abrir(Rotas.HISTORICO) },
                                    aoAbrirLancamentos = { navegarParaAba(navController, Rotas.LANCAMENTOS) },
                                    aoAbrirConta = { abrir(Rotas.CONTA) },
                                    aoAlternarValores = privacidadeViewModel::aoAlternar,
                                    aoCadastrarPropriedade = { abrir(Rotas.formPropriedade()) }
                                )
                            }

                            composable(Rotas.HISTORICO) {
                                // O mês escolhido já foi gravado em SelecaoAtual pelo ViewModel.
                                HistoricoRoute(aoAbrirMes = { navegarParaAba(navController, Rotas.LANCAMENTOS) })
                            }

                            composable(Rotas.LANCAMENTOS) {
                                LancamentoListaRoute(
                                    aoEditarLancamento = { id -> abrirFormulario(Rotas.formLancamento(id)) }
                                )
                            }

                            composable(Rotas.CLIMA) {
                                ClimaRoute(
                                    aoAbrirAjustes = { abrir(Rotas.AJUSTES_AVISO) },
                                    aoCadastrarPropriedade = { abrir(Rotas.formPropriedade()) }
                                )
                            }

                            composable(Rotas.AJUSTES_AVISO) {
                                AjustesAvisoRoute(
                                    aoConcluir = {
                                        if (navController.currentDestination?.route == Rotas.AJUSTES_AVISO) {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }

                            composable(Rotas.PROPRIEDADES) {
                                PropriedadeListaRoute(
                                    aoAbrirNova = { abrir(Rotas.formPropriedade()) },
                                    aoAbrirPropriedade = { id -> abrir(Rotas.formPropriedade(id)) },
                                    aoAbrirConta = { abrir(Rotas.CONTA) }
                                )
                            }

                            composable(
                                route = Rotas.FORM_LANCAMENTO,
                                arguments = listOf(
                                    navArgument(Rotas.ARG_ID) {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument(Rotas.ARG_COPIA) {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument(Rotas.ARG_TIPO) {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) { entrada ->
                                LancamentoFormRoute(
                                    lancamentoId = entrada.arguments?.getString(Rotas.ARG_ID),
                                    copiaDe = entrada.arguments?.getString(Rotas.ARG_COPIA),
                                    tipoInicial = Rotas.tipoDaRota(entrada.arguments?.getString(Rotas.ARG_TIPO)),
                                    // Guarda contra pop duplo durante a animação de saída.
                                    aoConcluir = {
                                        if (navController.currentDestination?.route == Rotas.FORM_LANCAMENTO) {
                                            navController.popBackStack()
                                        }
                                    },
                                    // Sem propriedade cadastrada: substitui o formulário pelo cadastro.
                                    aoCadastrarPropriedade = {
                                        navController.navigate(Rotas.formPropriedade()) {
                                            popUpTo(Rotas.FORM_LANCAMENTO) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    // Substitui a edição pela cópia, para "voltar" não retornar à edição.
                                    aoLancarDeNovo = { id ->
                                        navController.navigate(Rotas.formLancamento(copiaDe = id)) {
                                            popUpTo(Rotas.FORM_LANCAMENTO) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(
                                route = Rotas.FORM_PROPRIEDADE,
                                arguments = listOf(
                                    navArgument(Rotas.ARG_ID) {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) { entrada ->
                                PropriedadeFormRoute(
                                    propriedadeId = entrada.arguments?.getString(Rotas.ARG_ID),
                                    // Guarda contra pop duplo.
                                    aoConcluir = {
                                        if (navController.currentDestination?.route == Rotas.FORM_PROPRIEDADE) {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }
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

/**
 * Navegação entre abas sem empilhar destinos, preservando o estado de cada aba.
 *
 * popUpTo usa [Rotas.INICIO] e não o startDestination do grafo, que pode ser o login
 * (já removido da back stack após autenticar).
 */
private fun navegarParaAba(navController: NavHostController, rota: String) {
    navController.navigate(rota) {
        popUpTo(Rotas.INICIO) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Barra inferior flutuante em pílula, com o botão de novo lançamento no centro.
 * Rótulos usam autoSize para não truncar em telas estreitas.
 */
@Composable
private fun BarraInferior(
    rotaAtual: androidx.navigation.NavDestination?,
    aoSelecionar: (String) -> Unit,
    aoNovoLancamento: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 8.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.agro.cabecalho)
                .padding(6.dp)
                .selectableGroup()
        ) {
            ABAS.forEachIndexed { indice, aba ->
                // Botão "+" no centro da barra.
                if (indice == ABAS.size / 2) BotaoNovoLancamento(aoNovoLancamento)
                ItemDaBarra(
                    aba = aba,
                    selecionada = rotaAtual?.hierarchy?.any { it.route == aba.rota } == true,
                    aoClicar = { aoSelecionar(aba.rota) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.ItemDaBarra(aba: Aba, selecionada: Boolean, aoClicar: () -> Unit) {
    val corDoIcone = if (selecionada) MaterialTheme.agro.destaque else MaterialTheme.agro.sobreCabecalho
    val corDoNome = if (selecionada) MaterialTheme.agro.destaque else MaterialTheme.agro.sobreCabecalhoSecundario
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(if (selecionada) MaterialTheme.agro.cabecalhoElevado else Color.Transparent)
            .selectable(selected = selecionada, role = Role.Tab, onClick = aoClicar)
    ) {
        Icon(aba.icone, contentDescription = null, tint = corDoIcone, modifier = Modifier.size(22.dp))
        BasicText(
            text = aba.rotulo,
            style = MaterialTheme.typography.labelSmall.copy(color = corDoNome),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp),
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun BotaoNovoLancamento(aoClicar: () -> Unit) {
    Surface(
        onClick = aoClicar,
        shape = CircleShape,
        color = MaterialTheme.agro.destaque,
        contentColor = MaterialTheme.agro.sobreDestaque,
        modifier = Modifier
            .size(width = 60.dp, height = 52.dp)
            .semantics { contentDescription = "Novo lançamento" }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(IconesAgro.Mais, contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}
