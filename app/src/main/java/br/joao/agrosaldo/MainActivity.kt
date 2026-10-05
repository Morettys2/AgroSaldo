package br.joao.agrosaldo

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import br.joao.agrosaldo.aviso.NotificadorDeChuva
import br.joao.agrosaldo.core.navigation.AppNavegacao
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import java.time.YearMonth

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
        // Tela de abertura: o broto sobre o verde-noite, igual em todo Android
        // a partir do 8.0. Precisa vir antes do super.onCreate, que é quando
        // o tema da abertura dá lugar ao tema normal do app.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // A partir do Android 15 o app desenha atrás das barras do sistema.
        // O Scaffold dentro de AppNavegacao devolve o espaço delas em padding.
        enableEdgeToEdge()

        // Abriu o app (e não só girou a tela): começa no mês corrente. O
        // processo pode ter ficado vivo desde o mês passado.
        if (savedInstanceState == null) SelecaoAtual.selecionarMes(YearMonth.now())

        // Tocou na notificação de chuva? Ela traz este extra dizendo para onde ir.
        val abrirClima = intent?.getStringExtra(NotificadorDeChuva.EXTRA_DESTINO) == NotificadorDeChuva.DESTINO_CLIMA

        setContent {
            AgroSaldoTheme {
                AppNavegacao(abrirClima = abrirClima)
            }
        }
    }
}
