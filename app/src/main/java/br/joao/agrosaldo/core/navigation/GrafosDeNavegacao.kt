package br.joao.agrosaldo.core.navigation

import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
import br.joao.agrosaldo.feature.historico.HistoricoRoute
import br.joao.agrosaldo.feature.inicio.InicioRoute
import br.joao.agrosaldo.feature.lancamento.LancamentoFormRoute
import br.joao.agrosaldo.feature.lancamento.LancamentoListaRoute
import br.joao.agrosaldo.feature.propriedade.PropriedadeFormRoute
import br.joao.agrosaldo.feature.propriedade.PropriedadeListaRoute

/*
 * Os destinos do app, divididos por área. O AppNavegacao só chama as três funções
 * dentro do NavHost; cada uma registra as telas da sua área e liga os callbacks delas
 * ao [Navegador]. As telas não conhecem o NavController: recebem lambdas, o que as mantém
 * testáveis e com @Preview.
 */

/** Login, cadastro, consentimento LGPD, política e a área "Conta". */
internal fun NavGraphBuilder.grafoDaConta(navegador: Navegador) {
    // Fluxo não autenticado

    composable(Rotas.LOGIN) {
        LoginRoute(aoCriarConta = { navegador.abrir(Rotas.CRIAR_CONTA) })
    }

    composable(Rotas.CRIAR_CONTA) {
        // Escopo da entrada CRIAR_CONTA; compartilhado com CONSENTIMENTO.
        val cadastroViewModel: CadastroViewModel = viewModel(factory = CadastroViewModel.Factory)
        CadastroRoute(
            viewModel = cadastroViewModel,
            aoIrParaConsentimento = { navegador.abrir(Rotas.CONSENTIMENTO) },
            aoVoltarParaLogin = navegador::voltar
        )
    }

    composable(Rotas.CONSENTIMENTO) { entrada ->
        // Reutiliza o CadastroViewModel da entrada CRIAR_CONTA. Precisa de remember:
        // após criar a conta a back stack é limpa e getBackStackEntry lançaria
        // exceção durante a animação de saída desta tela.
        val cadastroViewModel = remember(entrada) {
            val donoDoCadastro = navegador.controle.getBackStackEntry(Rotas.CRIAR_CONTA)
            val provedor = ViewModelProvider(donoDoCadastro, CadastroViewModel.Factory)
            provedor[CadastroViewModel::class.java]
        }
        ConsentimentoRoute(
            viewModel = cadastroViewModel,
            aoLerPolitica = { navegador.abrir(Rotas.POLITICA) },
            // Recusa não persiste nada.
            aoRecusar = { navegador.controle.popBackStack(Rotas.LOGIN, inclusive = false) }
        )
    }

    composable(Rotas.POLITICA) {
        PoliticaScreen()
    }

    composable(Rotas.NOVO_CONSENTIMENTO) {
        // A navegação após aceitar/recusar vem do LaunchedEffect de sessão.
        NovoConsentimentoRoute(aoLerPolitica = { navegador.abrir(Rotas.POLITICA) })
    }

    // Área autenticada

    composable(Rotas.CONTA) {
        ContaRoute(
            aoAbrirPrivacidade = { navegador.abrir(Rotas.POLITICA) },
            aoAlterarSenha = { navegador.abrir(Rotas.ALTERAR_SENHA) },
            aoAbrirAvisoChuva = { navegador.abrir(Rotas.AJUSTES_AVISO) },
            aoAbrirPropriedades = { navegador.irParaAba(Rotas.PROPRIEDADES) }
        )
    }

    composable(Rotas.ALTERAR_SENHA) {
        AlterarSenhaRoute(aoConcluir = { navegador.fecharSeNoTopo(Rotas.ALTERAR_SENHA) })
    }
}

/**
 * O dinheiro: Início, lançamentos, histórico e propriedades, com os dois formulários.
 *
 * @param aoAlternarValores o "olho" do Início, que esconde os valores em todas as telas.
 */
internal fun NavGraphBuilder.grafoDoFinanceiro(navegador: Navegador, aoAlternarValores: () -> Unit) {
    composable(Rotas.INICIO) {
        InicioRoute(
            aoNovoLancamento = { tipo -> navegador.abrirFormularioDeLancamento(Rotas.formLancamento(tipo = tipo)) },
            aoEditarLancamento = { id -> navegador.abrirFormularioDeLancamento(Rotas.formLancamento(id)) },
            aoAbrirClima = { navegador.irParaAba(Rotas.CLIMA) },
            aoAbrirHistorico = { navegador.abrir(Rotas.HISTORICO) },
            aoAbrirLancamentos = { navegador.irParaAba(Rotas.LANCAMENTOS) },
            aoAbrirConta = { navegador.abrir(Rotas.CONTA) },
            aoAlternarValores = aoAlternarValores,
            aoCadastrarPropriedade = { navegador.abrir(Rotas.formPropriedade()) }
        )
    }

    composable(Rotas.HISTORICO) {
        // O mês escolhido já foi gravado em SelecaoAtual pelo ViewModel.
        HistoricoRoute(aoAbrirMes = { navegador.irParaAba(Rotas.LANCAMENTOS) })
    }

    composable(Rotas.LANCAMENTOS) {
        LancamentoListaRoute(
            aoEditarLancamento = { id -> navegador.abrirFormularioDeLancamento(Rotas.formLancamento(id)) }
        )
    }

    composable(Rotas.PROPRIEDADES) {
        PropriedadeListaRoute(
            aoAbrirNova = { navegador.abrir(Rotas.formPropriedade()) },
            aoAbrirPropriedade = { id -> navegador.abrir(Rotas.formPropriedade(id)) },
            aoAbrirConta = { navegador.abrir(Rotas.CONTA) }
        )
    }

    composable(
        route = Rotas.FORM_LANCAMENTO,
        arguments = listOf(
            argumentoOpcional(Rotas.ARG_ID),
            argumentoOpcional(Rotas.ARG_COPIA),
            argumentoOpcional(Rotas.ARG_TIPO)
        )
    ) { entrada ->
        LancamentoFormRoute(
            lancamentoId = entrada.arguments?.getString(Rotas.ARG_ID),
            copiaDe = entrada.arguments?.getString(Rotas.ARG_COPIA),
            tipoInicial = Rotas.tipoDaRota(entrada.arguments?.getString(Rotas.ARG_TIPO)),
            // Guarda contra pop duplo durante a animação de saída.
            aoConcluir = { navegador.fecharSeNoTopo(Rotas.FORM_LANCAMENTO) },
            // Sem propriedade cadastrada: substitui o formulário pelo cadastro.
            aoCadastrarPropriedade = { navegador.substituir(Rotas.FORM_LANCAMENTO, Rotas.formPropriedade()) },
            // Substitui a edição pela cópia, para "voltar" não retornar à edição.
            aoLancarDeNovo = { id ->
                navegador.controle.navigate(Rotas.formLancamento(copiaDe = id)) {
                    popUpTo(Rotas.FORM_LANCAMENTO) { inclusive = true }
                }
            }
        )
    }

    composable(
        route = Rotas.FORM_PROPRIEDADE,
        arguments = listOf(argumentoOpcional(Rotas.ARG_ID))
    ) { entrada ->
        PropriedadeFormRoute(
            propriedadeId = entrada.arguments?.getString(Rotas.ARG_ID),
            aoConcluir = { navegador.fecharSeNoTopo(Rotas.FORM_PROPRIEDADE) }
        )
    }
}

/** Previsão do tempo e ajustes do aviso de chuva. */
internal fun NavGraphBuilder.grafoDoClima(navegador: Navegador) {
    composable(Rotas.CLIMA) {
        ClimaRoute(
            aoAbrirAjustes = { navegador.abrir(Rotas.AJUSTES_AVISO) },
            aoCadastrarPropriedade = { navegador.abrir(Rotas.formPropriedade()) }
        )
    }

    composable(Rotas.AJUSTES_AVISO) {
        AjustesAvisoRoute(aoConcluir = { navegador.fecharSeNoTopo(Rotas.AJUSTES_AVISO) })
    }
}

/** Argumento de rota texto, opcional e nulo por padrão (?id=…). */
private fun argumentoOpcional(nome: String) = navArgument(nome) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}
