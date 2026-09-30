package br.joao.agrosaldo.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.joao.agrosaldo.feature.clima.ClimaScreen
import br.joao.agrosaldo.feature.inicio.InicioRoute
import br.joao.agrosaldo.feature.lancamento.LancamentoFormRoute
import br.joao.agrosaldo.feature.lancamento.LancamentoListaRoute
import br.joao.agrosaldo.feature.propriedade.PropriedadeFormRoute
import br.joao.agrosaldo.feature.propriedade.PropriedadeListaRoute

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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavegacao(
    navController: NavHostController = rememberNavController()
) {
    val entradaAtual by navController.currentBackStackEntryAsState()
    val rotaAtual = entradaAtual?.destination?.route

    val ehAbaPrincipal = ABAS.any { it.rota == rotaAtual }

    val titulo = when (rotaAtual) {
        Rotas.CLIMA -> "Previsão do tempo"
        Rotas.PROPRIEDADES -> "Propriedades"
        Rotas.FORM_LANCAMENTO ->
            if (entradaAtual?.arguments?.getString(Rotas.ARG_ID) == null) "Novo lançamento"
            else "Editar lançamento"
        Rotas.FORM_PROPRIEDADE ->
            if (entradaAtual?.arguments?.getString(Rotas.ARG_ID) == null) "Nova propriedade"
            else "Editar propriedade"
        // Início e Lançamentos têm o seletor de propriedade como cabeçalho.
        else -> null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                    aoSelecionar = { rota -> navegarParaAba(navController, rota) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { espacoInterno ->

        NavHost(
            navController = navController,
            startDestination = Rotas.INICIO,
            modifier = Modifier.padding(espacoInterno)
        ) {

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
                ClimaScreen()
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
 */
private fun navegarParaAba(navController: NavHostController, rota: String) {
    navController.navigate(rota) {
        popUpTo(navController.graph.findStartDestination().id) {
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
