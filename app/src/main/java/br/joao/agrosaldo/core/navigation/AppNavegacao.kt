package br.joao.agrosaldo.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import br.joao.agrosaldo.feature.clima.AjustesAvisoRoute
import br.joao.agrosaldo.feature.clima.ClimaRoute
import br.joao.agrosaldo.feature.conta.AlterarSenhaRoute
import br.joao.agrosaldo.feature.conta.CadastroRoute
import br.joao.agrosaldo.feature.conta.CadastroViewModel
import br.joao.agrosaldo.feature.conta.ConsentimentoRoute
import br.joao.agrosaldo.feature.conta.ContaRoute
import br.joao.agrosaldo.feature.conta.LoginRoute
import br.joao.agrosaldo.feature.conta.NovoConsentimentoRoute
import br.joao.agrosaldo.feature.conta.PoliticaScreen
import br.joao.agrosaldo.feature.conta.TelaBloqueioRoute
import br.joao.agrosaldo.feature.inicio.InicioRoute
import br.joao.agrosaldo.feature.lancamento.LancamentoFormRoute
import br.joao.agrosaldo.feature.lancamento.LancamentoListaRoute
import br.joao.agrosaldo.feature.propriedade.PropriedadeFormRoute
import br.joao.agrosaldo.feature.propriedade.PropriedadeListaRoute
import kotlinx.coroutines.flow.first

/** Uma aba da barra inferior. */
private data class Aba(
    val rota: String,
    val rotulo: String,
    val icone: ImageVector,
    val iconeSelecionado: ImageVector
)

private val ABAS = listOf(
    Aba(Rotas.INICIO, "Início", Icons.Outlined.Home, Icons.Filled.Home),
    Aba(Rotas.LANCAMENTOS, "Lançamentos", Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Filled.List),
    Aba(Rotas.CLIMA, "Clima", Icons.Outlined.Cloud, Icons.Filled.Cloud),
    Aba(Rotas.PROPRIEDADES, "Sítios", Icons.Outlined.Grass, Icons.Filled.Grass)
)

/**
 * Esqueleto do app: barra superior, conteúdo e barra inferior.
 *
 * É aqui — e só aqui — que o app decide para onde ir. As telas apenas
 * avisam "tocaram em novo lançamento" por meio de uma função; quem traduz
 * isso em navegação é este arquivo.
 *
 * Por isso nenhuma tela recebe NavController. Se recebesse, ficaria presa
 * a este grafo de navegação e o @Preview dela quebraria.
 *
 * Login e logout seguem a mesma ideia: as telas só mexem na sessão, e este
 * arquivo observa [SessaoViewModel.logado] para trocar de tela.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavegacao(
    abrirClima: Boolean = false,
    navController: NavHostController = rememberNavController(),
    sessaoViewModel: SessaoViewModel = viewModel(factory = SessaoViewModel.Factory)
) {
    val logado by sessaoViewModel.logado.collectAsStateWithLifecycle()
    val precisaNovoAceite by sessaoViewModel.precisaNovoAceite.collectAsStateWithLifecycle()
    val precisaDesbloquear by sessaoViewModel.precisaDesbloquear.collectAsStateWithLifecycle()

    // Decidido UMA vez, ao abrir o app. Depois disso quem troca de tela é o
    // LaunchedEffect abaixo — mudar o destino inicial de um NavHost que já
    // está na tela não tem efeito.
    val destinoInicial = remember { if (logado) Rotas.INICIO else Rotas.LOGIN }

    /**
     * Reage a entrar, sair e à política nova. popUpTo(graph.id) com inclusive
     * limpa a pilha inteira: depois de entrar, o "voltar" não leva de novo ao
     * login; depois de sair, não leva de volta para dentro da conta; e não
     * dá para "voltar" por cima do novo aceite.
     */
    LaunchedEffect(logado, precisaNovoAceite) {
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

    /**
     * Mede quanto tempo o app ficou fora da tela, para travar de novo depois
     * de 2 minutos. ON_STOP/ON_START, e não ON_PAUSE: uma janela do sistema
     * por cima (como a própria digital) pausa o app, mas não o tira da tela.
     */
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

    // Veio do toque na notificação de chuva: vai direto para a aba Clima, se
    // houver alguém conectado (senão, o login aparece primeiro). Se a política
    // tiver mudado, o efeito de cima passa o novo aceite na frente.
    // rememberSaveable: ao girar o celular a Activity é recriada com o mesmo
    // intent, e sem isto a tela pularia de novo para o Clima.
    var jaAbriuClima by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(abrirClima) {
        if (abrirClima && logado && !jaAbriuClima) {
            // Espera o NavHost montar a primeira tela antes de navegar.
            snapshotFlow { navController.currentBackStackEntry }.first { it != null }
            jaAbriuClima = true
            navegarParaAba(navController, Rotas.CLIMA)
        }
    }

    // Travou com um campo em foco: tira o foco, para o teclado não voltar a
    // aparecer por cima da tela de bloqueio.
    val foco = LocalFocusManager.current
    val contexto = LocalContext.current
    LaunchedEffect(precisaDesbloquear) {
        if (precisaDesbloquear) foco.clearFocus(force = true)
    }

    val entradaAtual by navController.currentBackStackEntryAsState()
    val rotaAtual = entradaAtual?.destination?.route

    val ehAbaPrincipal = ABAS.any { it.rota == rotaAtual }

    val titulo = when (rotaAtual) {
        Rotas.PROPRIEDADES -> "Propriedades"
        Rotas.CRIAR_CONTA -> "Criar conta"
        Rotas.CONTA -> "Conta"
        Rotas.ALTERAR_SENHA -> "Alterar senha"
        Rotas.AJUSTES_AVISO -> "Aviso de chuva"
        Rotas.POLITICA -> "Privacidade e dados"
        Rotas.FORM_LANCAMENTO ->
            if (entradaAtual?.arguments?.getString(Rotas.ARG_ID) == null) "Novo lançamento"
            else "Editar lançamento"
        Rotas.FORM_PROPRIEDADE ->
            if (entradaAtual?.arguments?.getString(Rotas.ARG_ID) == null) "Nova propriedade"
            else "Editar propriedade"
        // Início, Lançamentos e Clima têm o seletor de propriedade como cabeçalho.
        // Login e consentimento têm a marca do app no lugar da barra.
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // Travado: o conteúdo continua montado embaixo (para voltar na mesma
            // tela, com o que estava digitado), mas some para o leitor de tela.
            modifier = Modifier
                .fillMaxSize()
                .then(if (precisaDesbloquear) Modifier.clearAndSetSemantics { } else Modifier),
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
                        actions = {
                            // Tela 12 do Figma: a conta abre pelo canto da lista de propriedades.
                            if (rotaAtual == Rotas.PROPRIEDADES) {
                                IconButton(onClick = { navController.navigate(Rotas.CONTA) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = "Abrir conta"
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
                // Formulários escondem a barra inferior: é uma tarefa que precisa
                // ser concluída ou cancelada, não um lugar pra onde se navega.
                if (ehAbaPrincipal) {
                    BarraInferior(
                        rotaAtual = entradaAtual?.destination,
                        aoSelecionar = { rota -> navegarParaAba(navController, rota) }
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

                // ---------- entrada ----------

                composable(Rotas.LOGIN) {
                    LoginRoute(aoCriarConta = { navController.navigate(Rotas.CRIAR_CONTA) })
                }

                composable(Rotas.CRIAR_CONTA) {
                    // Este ViewModel fica guardado na entrada CRIAR_CONTA da
                    // pilha. O consentimento, logo abaixo, pega o mesmo.
                    val cadastroViewModel: CadastroViewModel = viewModel(factory = CadastroViewModel.Factory)
                    CadastroRoute(
                        viewModel = cadastroViewModel,
                        aoIrParaConsentimento = { navController.navigate(Rotas.CONSENTIMENTO) },
                        aoVoltarParaLogin = { navController.popBackStack() }
                    )
                }

                composable(Rotas.CONSENTIMENTO) { entrada ->
                    // Duas telas dividindo um ViewModel: pedimos o dono dele — a
                    // entrada CRIAR_CONTA, que continua na pilha por baixo — e o
                    // ViewModel guardado nela. Como ele já existe lá, vem o mesmo
                    // objeto, com o formulário preenchido.
                    //
                    // Fica dentro de remember de propósito: depois que a conta é
                    // criada, a pilha inteira é limpa e a entrada CRIAR_CONTA deixa
                    // de existir enquanto esta tela ainda anima a saída. Buscar de
                    // novo nesse instante derrubaria o app; com remember, a busca
                    // acontece uma vez só.
                    val cadastroViewModel = remember(entrada) {
                        val donoDoCadastro = navController.getBackStackEntry(Rotas.CRIAR_CONTA)
                        ViewModelProvider(donoDoCadastro, CadastroViewModel.Factory)[CadastroViewModel::class.java]
                    }
                    ConsentimentoRoute(
                        viewModel = cadastroViewModel,
                        aoLerPolitica = { navController.navigate(Rotas.POLITICA) },
                        // Recusar volta para o login sem ter gravado nada.
                        aoRecusar = { navController.popBackStack(Rotas.LOGIN, inclusive = false) }
                    )
                }

                composable(Rotas.POLITICA) {
                    PoliticaScreen()
                }

                composable(Rotas.NOVO_CONSENTIMENTO) {
                    // Aceitar ou recusar só mexe na sessão; o LaunchedEffect lá
                    // em cima leva para o Início ou para o login.
                    NovoConsentimentoRoute(aoLerPolitica = { navController.navigate(Rotas.POLITICA) })
                }

                // ---------- dentro da conta ----------

                composable(Rotas.CONTA) {
                    ContaRoute(
                        aoAbrirPrivacidade = { navController.navigate(Rotas.POLITICA) },
                        aoAlterarSenha = { navController.navigate(Rotas.ALTERAR_SENHA) },
                        aoAbrirAvisoChuva = { navController.navigate(Rotas.AJUSTES_AVISO) },
                        aoAbrirPropriedades = { navegarParaAba(navController, Rotas.PROPRIEDADES) }
                    )
                }

                composable(Rotas.ALTERAR_SENHA) {
                    AlterarSenhaRoute(
                        aoConcluir = {
                            // Só volta se ainda estiver nesta tela: se a pessoa já
                            // tocou em voltar, um segundo pop fecharia a Conta também.
                            if (navController.currentDestination?.route == Rotas.ALTERAR_SENHA) {
                                navController.popBackStack()
                            }
                        }
                    )
                }

                composable(Rotas.INICIO) {
                    InicioRoute(
                        aoNovoLancamento = { navController.navigate(Rotas.formLancamento()) },
                        aoEditarLancamento = { id -> navController.navigate(Rotas.formLancamento(id)) },
                        aoAbrirClima = { navegarParaAba(navController, Rotas.CLIMA) },
                        aoCadastrarPropriedade = { navController.navigate(Rotas.formPropriedade()) }
                    )
                }

                composable(Rotas.LANCAMENTOS) {
                    LancamentoListaRoute(
                        aoNovoLancamento = { navController.navigate(Rotas.formLancamento()) },
                        aoEditarLancamento = { id -> navController.navigate(Rotas.formLancamento(id)) }
                    )
                }

                composable(Rotas.CLIMA) {
                    ClimaRoute(
                        aoAbrirAjustes = { navController.navigate(Rotas.AJUSTES_AVISO) },
                        aoCadastrarPropriedade = { navController.navigate(Rotas.formPropriedade()) }
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
                        aoAbrirNova = { navController.navigate(Rotas.formPropriedade()) },
                        aoAbrirPropriedade = { id -> navController.navigate(Rotas.formPropriedade(id)) }
                    )
                }

                composable(
                    route = Rotas.FORM_LANCAMENTO,
                    arguments = listOf(
                        navArgument(Rotas.ARG_ID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entrada ->
                    LancamentoFormRoute(
                        lancamentoId = entrada.arguments?.getString(Rotas.ARG_ID),
                        aoConcluir = { navController.popBackStack() }
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
                        aoConcluir = { navController.popBackStack() }
                    )
                }
            }

            // Voltar com o app travado manda o app para o fundo. Fica aqui,
            // depois do NavHost, para ter prioridade sobre o "voltar" dele
            // mesmo quando o app já abre travado.
            BackHandler(enabled = precisaDesbloquear) {
                contexto.acharFragmentActivity()?.moveTaskToBack(true)
            }
        }

        if (precisaDesbloquear) {
            TelaBloqueioRoute(
                aoDesbloquear = sessaoViewModel::aoDesbloquear,
                aoUsarSenha = sessaoViewModel::aoUsarSenha
            )
        }
    }
}

/**
 * Navegação entre abas com o comportamento padrão do Android:
 *
 * - popUpTo(início) evita empilhar abas: ir de Início para Sítios e voltar
 *   para Início não cria uma pilha de dez telas.
 * - saveState/restoreState guardam o scroll e o estado de cada aba ao sair
 *   e devolvem ao voltar.
 * - launchSingleTop impede abrir a mesma aba duas vezes ao tocar de novo.
 *
 * O popUpTo aponta para a rota do Início, e não para o "destino inicial do
 * grafo": quando o app abre no login, o destino inicial é o login, que já
 * saiu da pilha depois de entrar.
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

@Composable
private fun BarraInferior(
    rotaAtual: androidx.navigation.NavDestination?,
    aoSelecionar: (String) -> Unit
) {
    // NavigationBar do Material 3 já desenha o indicador em pílula atrás do
    // ícone selecionado — é o mesmo elemento que desenhamos no Figma.
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        ABAS.forEach { aba ->
            val selecionada = rotaAtual?.hierarchy?.any { it.route == aba.rota } == true
            NavigationBarItem(
                selected = selecionada,
                onClick = { aoSelecionar(aba.rota) },
                icon = {
                    Icon(
                        imageVector = if (selecionada) aba.iconeSelecionado else aba.icone,
                        contentDescription = null
                    )
                },
                label = { Text(aba.rotulo, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
