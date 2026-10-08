package br.joao.agrosaldo

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import br.joao.agrosaldo.aviso.NotificadorDeChuva
import br.joao.agrosaldo.core.navigation.AppNavegacao
import br.joao.agrosaldo.core.navigation.PedidoDoClima
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * Activity única: aplica o tema e delega a navegação para [AppNavegacao].
 *
 * Estende FragmentActivity porque o BiometricPrompt exige uma.
 */
class MainActivity : FragmentActivity() {

    /**
     * Último pedido da notificação de chuva para abrir o Clima; a navegação reage a cada
     * pedido novo (um objeto por toque, para que dois toques seguidos disparem).
     */
    private var pedidoDoClima by mutableStateOf<PedidoDoClima?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Deve vir antes de super.onCreate, que troca o tema da splash pelo tema do app.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Os insets das barras do sistema são tratados pelo Scaffold em AppNavegacao.
        enableEdgeToEdge()

        protegerTelaComOBloqueio()

        // Criação nova (não recriação): volta ao mês corrente, pois o processo pode ter
        // sobrevivido desde o mês anterior.
        if (savedInstanceState == null) {
            irParaOMesAtual()
            // Ignora o intent reentregue ao reabrir pelos recentes (LAUNCHED_FROM_HISTORY).
            val dosRecentes = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
            if (!dosRecentes) receberPedidoDoClima(intent)
        }

        setContent {
            AgroSaldoTheme {
                AppNavegacao(pedidoDoClima = pedidoDoClima)
            }
        }
    }

    /**
     * Toque na notificação com o app aberto (launchMode singleTop): evita recriar a
     * Activity e perder um formulário em edição.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receberPedidoDoClima(intent)
    }

    override fun onStart() {
        super.onStart()
        // Virada do mês com o app vivo: segue o mês corrente, preservando um mês escolhido à mão.
        SelecaoAtual.acompanharMesCorrente()
    }

    private fun irParaOMesAtual() {
        SelecaoAtual.selecionarMes(YearMonth.now())
    }

    /**
     * Com o bloqueio por digital ligado, a tela fica fora de capturas, gravações e da
     * miniatura de Recentes (FLAG_SECURE), que mostraria o saldo sem pedir a digital. No
     * Android 13+, Recentes também deixa de guardar a miniatura. Segue o ajuste ao vivo.
     */
    private fun protegerTelaComOBloqueio() {
        lifecycleScope.launch {
            Repositorios.bloqueio.ativo.collect { ativo ->
                if (ativo) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!ativo)
            }
        }
    }

    /** A propriedade vem de fora do app; a navegação só a seleciona se for do usuário conectado. */
    private fun receberPedidoDoClima(intent: Intent) {
        if (intent.getStringExtra(NotificadorDeChuva.EXTRA_DESTINO) != NotificadorDeChuva.DESTINO_CLIMA) return
        pedidoDoClima = PedidoDoClima(intent.getStringExtra(NotificadorDeChuva.EXTRA_PROPRIEDADE))
    }
}
