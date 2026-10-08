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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import br.joao.agrosaldo.data.Repositorios
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

/** Uma aba da barra inferior. */
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
    pedidosDoClima: Int = 0,
    navController: NavHostController = rememberNavController(),
    sessaoViewModel: SessaoViewModel = viewModel(factory = SessaoViewModel.Factory),
    privacidadeViewModel: PrivacidadeViewModel = viewModel(factory = PrivacidadeViewModel.Factory),
    desfazer: Desfazer = Repositorios.desfazer
) {
    val valoresOcultos by privacidadeViewModel.valoresOcultos.collectAsStateWithLifecycle()
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
    // Cada toque na notificação aumenta o número; girar o celular não.
    LaunchedEffect(pedidosDoClima) {
        if (pedidosDoClima > 0 && logado) {
            // Espera o NavHost montar a primeira tela antes de navegar.
            navController.currentBackStackEntryFlow.first()
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

    /**
     * "Lançamento excluído — Desfazer". A barra fica aqui, e não na tela que
     * excluiu, porque essa tela já fechou quando a barra aparece.
     *
     * Se a exclusão pendente mudar (outra exclusão, ou trocou de conta), o
     * LaunchedEffect reinicia e a barra antiga some sozinha.
     */
    val estadoDaBarra = remember { SnackbarHostState() }
    // Escopo da tela inteira: o aviso de erro abaixo não pode morrer junto
    // com o LaunchedEffect, que reinicia assim que a oferta sai de "pendente".
    val escopoDaBarra = rememberCoroutineScope()
    val exclusaoPendente by desfazer.pendente.collectAsStateWithLifecycle()
    LaunchedEffect(exclusaoPendente) {
        val exclusao = exclusaoPendente ?: return@LaunchedEffect
        // Reabriu o app muito depois: a oferta antiga não volta.
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

    val entradaAtual by navController.currentBackStackEntryAsState()
    val rotaAtual = entradaAtual?.destination?.route

    val ehAbaPrincipal = rotaAtual in Rotas.ABAS
    val cabecalhoEscuro = rotaAtual in Rotas.COM_CABECALHO_ESCURO

    // Ícones da barra de status (relógio, bateria): claros sobre o cabeçalho
    // verde-noite e no modo escuro; escuros nas telas claras. Sem isto, o
    // relógio ficaria preto sobre o verde-escuro, quase invisível.
    val modoEscuro = isSystemInDarkTheme()
    val vista = LocalView.current
    if (!vista.isInEditMode) {
        SideEffect {
            contexto.acharFragmentActivity()?.window?.let { janela ->
                // A tela de bloqueio é clara e fica por cima de tudo.
                WindowCompat.getInsetsController(janela, vista).isAppearanceLightStatusBars =
                    !modoEscuro && (precisaDesbloquear || !cabecalhoEscuro)
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
        // As abas e o novo lançamento têm o próprio cabeçalho verde-noite.
        // Login e consentimento têm a marca do app no lugar da barra.
        else -> null
    }

    /**
     * Abre o formulário de lançamento, se ele já não estiver aberto. Dois
     * toques rápidos no "+" (ou num atalho) empilhariam dois formulários.
     */
    val abrirFormulario: (String) -> Unit = { rota ->
        if (navController.currentDestination?.route != Rotas.FORM_LANCAMENTO) navController.navigate(rota)
    }
    val abrirNovoLancamento = { abrirFormulario(Rotas.formLancamento()) }

    // O "olho" do Início vale para o app inteiro: toda tela que mostra
    // dinheiro lê LocalValoresOcultos.
    CompositionLocalProvider(LocalValoresOcultos provides valoresOcultos) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                // Travado: o conteúdo continua montado embaixo (para voltar na mesma
                // tela, com o que estava digitado), mas some para o leitor de tela.
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (precisaDesbloquear) Modifier.clearAndSetSemantics { } else Modifier),
                // O Scaffold já põe a barra do "Desfazer" acima da barra de baixo.
                snackbarHost = {
                    SnackbarHost(
                        hostState = estadoDaBarra,
                        // No formulário não há barra de baixo: sem isto, o "Desfazer"
                        // ficaria em cima do botão de salvar.
                        modifier = if (rotaAtual == Rotas.FORM_LANCAMENTO) {
                            Modifier
                                .navigationBarsPadding()
                                .padding(bottom = 84.dp)
                        } else {
                            Modifier
                        }
                    )
                },
                // Com o cabeçalho verde-noite, a tela vai até o topo e o próprio
                // cabeçalho se afasta do relógio. Nas outras, o Scaffold reserva o espaço.
                // Os lados continuam valendo: deitado, com a barra de navegação ou
                // o recorte da câmera do lado, nada fica escondido embaixo deles.
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
                    // Formulários escondem a barra inferior: é uma tarefa que precisa
                    // ser concluída ou cancelada, não um lugar pra onde se navega.
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
                            aoNovoLancamento = { tipo -> abrirFormulario(Rotas.formLancamento(tipo = tipo)) },
                            aoEditarLancamento = { id -> abrirFormulario(Rotas.formLancamento(id)) },
                            aoAbrirClima = { navegarParaAba(navController, Rotas.CLIMA) },
                            aoAbrirHistorico = { navController.navigate(Rotas.HISTORICO) },
                            aoAbrirLancamentos = { navegarParaAba(navController, Rotas.LANCAMENTOS) },
                            aoAbrirConta = { navController.navigate(Rotas.CONTA) },
                            aoAlternarValores = privacidadeViewModel::aoAlternar,
                            aoCadastrarPropriedade = { navController.navigate(Rotas.formPropriedade()) }
                        )
                    }

                    composable(Rotas.HISTORICO) {
                        // Tocar num mês abre a aba Lançamentos naquele mês: o
                        // ViewModel já deixou o mês escolhido em SelecaoAtual.
                        HistoricoRoute(aoAbrirMes = { navegarParaAba(navController, Rotas.LANCAMENTOS) })
                    }

                    composable(Rotas.LANCAMENTOS) {
                        LancamentoListaRoute(
                            aoEditarLancamento = { id -> abrirFormulario(Rotas.formLancamento(id)) }
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
                            aoAbrirPropriedade = { id -> navController.navigate(Rotas.formPropriedade(id)) },
                            // Tela 12 do Figma: a conta abre pelo canto da lista de propriedades.
                            aoAbrirConta = { navController.navigate(Rotas.CONTA) }
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
                            // Só fecha se ainda estiver nele: o X tocado duas vezes durante a
                            // animação de saída fecharia também a aba de baixo.
                            aoConcluir = {
                                if (navController.currentDestination?.route == Rotas.FORM_LANCAMENTO) {
                                    navController.popBackStack()
                                }
                            },
                            // Sem nenhum sítio: troca o formulário pelo cadastro do sítio.
                            aoCadastrarPropriedade = {
                                navController.navigate(Rotas.formPropriedade()) {
                                    popUpTo(Rotas.FORM_LANCAMENTO) { inclusive = true }
                                }
                            },
                            // Troca a edição pela cópia: o "voltar" da cópia leva
                            // para a lista, não de volta para a edição.
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
                            // Mesma proteção do formulário de lançamento: salvar logo
                            // depois de tocar em "voltar" não fecha a tela de baixo também.
                            aoConcluir = {
                                if (navController.currentDestination?.route == Rotas.FORM_PROPRIEDADE) {
                                    navController.popBackStack()
                                }
                            }
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

/**
 * A barra de baixo da direção "Safra": uma pílula verde-noite que flutua
 * sobre a tela, com o "+" verde-limão no meio.
 *
 * Cada aba tem ícone E nome embaixo. Só ícone seria mais bonito, mas o
 * produtor precisaria adivinhar o que é a nuvem e o broto; com o nome, não.
 * O nome encolhe um pouco se não couber ("Lançamentos" num celular
 * estreito), em vez de ser cortado.
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
                // O "+" fica no meio, entre a segunda e a terceira aba.
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
