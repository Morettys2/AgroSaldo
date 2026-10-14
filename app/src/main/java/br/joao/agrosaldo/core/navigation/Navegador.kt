package br.joao.agrosaldo.core.navigation

import androidx.compose.runtime.Stable
import androidx.navigation.NavHostController

/**
 * As formas de navegar do app, num lugar só, em cima do [NavHostController].
 *
 * Cada função resolve um problema que aparecia em vários destinos: toque duplo empilhando
 * a mesma tela, "voltar" duplo durante a animação de saída, troca de aba perdendo o
 * estado. Os grafos ([grafoDaConta], [grafoDoFinanceiro], [grafoDoClima]) só chamam
 * estas funções, sem repetir a regra.
 */
@Stable
class Navegador(val controle: NavHostController) {

    /** Abre [rota] por cima da atual. Com launchSingleTop, um toque duplo não empilha duas telas iguais. */
    fun abrir(rota: String) {
        controle.navigate(rota) { launchSingleTop = true }
    }

    /** Abre o formulário de lançamento, ignorando se ele já está no topo (toques rápidos). */
    fun abrirFormularioDeLancamento(rota: String) {
        if (controle.currentDestination?.route != Rotas.FORM_LANCAMENTO) controle.navigate(rota)
    }

    /**
     * Troca de aba sem empilhar destinos, preservando o estado de cada aba.
     *
     * popUpTo usa [Rotas.INICIO] e não o startDestination do grafo, que pode ser o login
     * (já removido da back stack após autenticar).
     */
    fun irParaAba(rota: String) {
        controle.navigate(rota) {
            popUpTo(Rotas.INICIO) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun voltar() {
        controle.popBackStack()
    }

    /**
     * Fecha a tela [rota] só se ela ainda estiver no topo. Evita o "voltar" duplo quando a
     * tela termina (salvou, excluiu) depois de a pessoa já ter voltado.
     */
    fun fecharSeNoTopo(rota: String) {
        if (controle.currentDestination?.route == rota) controle.popBackStack()
    }

    /** Navega para [rota] tirando [substituida] da pilha: "voltar" não retorna a ela. */
    fun substituir(substituida: String, rota: String) {
        controle.navigate(rota) {
            popUpTo(substituida) { inclusive = true }
            launchSingleTop = true
        }
    }
}
