package br.joao.agrosaldo

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import br.joao.agrosaldo.aviso.NotificadorDeChuva
import br.joao.agrosaldo.core.navigation.AppNavegacao
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme

/**
 * A Activity ficou mínima de propósito.
 *
 * Em Compose, a Activity é só a porta de entrada: liga o tema e entrega o
 * controle para a navegação. Toda lógica de tela mora nos ViewModels, e
 * toda decisão de rota mora em AppNavegacao.
 *
 * É FragmentActivity (e não ComponentActivity) por um motivo só: a janela
 * da digital do Android (BiometricPrompt) precisa de uma. FragmentActivity
 * é uma ComponentActivity com suporte a fragments — o Compose funciona igual.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A partir do Android 15 o app desenha atrás das barras do sistema.
        // O Scaffold dentro de AppNavegacao devolve o espaço delas em padding.
        enableEdgeToEdge()

        // Tocou na notificação de chuva? Ela traz este extra dizendo para onde ir.
        val abrirClima = intent?.getStringExtra(NotificadorDeChuva.EXTRA_DESTINO) == NotificadorDeChuva.DESTINO_CLIMA

        setContent {
            AgroSaldoTheme {
                AppNavegacao(abrirClima = abrirClima)
            }
        }
    }
}
