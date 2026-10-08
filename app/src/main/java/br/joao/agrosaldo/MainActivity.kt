package br.joao.agrosaldo

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import br.joao.agrosaldo.aviso.NotificadorDeChuva
import br.joao.agrosaldo.core.navigation.AppNavegacao
import br.joao.agrosaldo.core.navigation.PedidoDoClima
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
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
        // Virada de mês com o app vivo: avança para o mês novo. Uma seleção manual de
        // outro mês só é sobrescrita na virada.
        if (YearMonth.now() != mesMostradoAoAbrir) irParaOMesAtual()
    }

    private fun irParaOMesAtual() {
        val agora = YearMonth.now()
        mesMostradoAoAbrir = agora
        SelecaoAtual.selecionarMes(agora)
    }

    /** A propriedade vem de fora do app; a navegação só a seleciona se for do usuário conectado. */
    private fun receberPedidoDoClima(intent: Intent) {
        if (intent.getStringExtra(NotificadorDeChuva.EXTRA_DESTINO) != NotificadorDeChuva.DESTINO_CLIMA) return
        pedidoDoClima = PedidoDoClima(intent.getStringExtra(NotificadorDeChuva.EXTRA_PROPRIEDADE))
    }

    private companion object {
        /** No companion para sobreviver à recriação da Activity; dura o processo, como [SelecaoAtual]. */
        var mesMostradoAoAbrir: YearMonth? = null
    }
}
