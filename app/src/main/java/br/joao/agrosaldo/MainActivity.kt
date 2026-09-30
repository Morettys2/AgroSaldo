package br.joao.agrosaldo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.joao.agrosaldo.core.navigation.AppNavegacao
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme

/**
 * A Activity ficou mínima de propósito.
 *
 * Em Compose, a Activity é só a porta de entrada: liga o tema e entrega o
 * controle para a navegação. Toda lógica de tela mora nos ViewModels, e
 * toda decisão de rota mora em AppNavegacao.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A partir do Android 15 o app desenha atrás das barras do sistema.
        // O Scaffold dentro de AppNavegacao devolve o espaço delas em padding.
        enableEdgeToEdge()

        setContent {
            AgroSaldoTheme {
                AppNavegacao()
            }
        }
    }
}
