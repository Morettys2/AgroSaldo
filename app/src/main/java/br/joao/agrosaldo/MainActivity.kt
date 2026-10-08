package br.joao.agrosaldo

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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

    /**
     * Quantas vezes a notificação de chuva pediu para abrir o Clima. A
     * navegação reage a cada número novo. Um contador, e não um sim/não,
     * porque o mesmo pedido pode chegar duas vezes seguidas (duas notificações).
     */
    private var pedidosDoClima by mutableIntStateOf(0)

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
        if (savedInstanceState == null) {
            irParaOMesAtual()
            // Abriu tocando na notificação de chuva? Girar a tela não conta de
            // novo, nem reabrir pela lista de apps recentes (o Android entrega
            // a mesma intenção de antes, com a marca LAUNCHED_FROM_HISTORY).
            val dosRecentes = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
            if (intent.veioDaNotificacaoDeChuva() && !dosRecentes) pedidosDoClima++
        }

        setContent {
            AgroSaldoTheme {
                AppNavegacao(pedidosDoClima = pedidosDoClima)
            }
        }
    }

    /**
     * Com o app já aberto, tocar na notificação chega aqui (launchMode
     * singleTop no manifesto), em vez de fechar e recriar a tela inteira —
     * o que perderia um lançamento sendo digitado.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.veioDaNotificacaoDeChuva()) pedidosDoClima++
    }

    override fun onStart() {
        super.onStart()
        // Deixou o app aberto (ou no fundo) de um mês para o outro: mostra o
        // mês novo. Se a pessoa escolheu outro mês, isso só acontece na virada.
        if (YearMonth.now() != mesMostradoAoAbrir) irParaOMesAtual()
    }

    private fun irParaOMesAtual() {
        val agora = YearMonth.now()
        mesMostradoAoAbrir = agora
        SelecaoAtual.selecionarMes(agora)
    }

    private fun Intent?.veioDaNotificacaoDeChuva(): Boolean =
        this?.getStringExtra(NotificadorDeChuva.EXTRA_DESTINO) == NotificadorDeChuva.DESTINO_CLIMA

    private companion object {
        /**
         * Fica no companion, e não na Activity, porque a Activity é recriada
         * (tema escuro que liga sozinho, girar a tela) e esqueceria. Vive
         * enquanto o processo do app viver — igual à seleção de mês.
         */
        var mesMostradoAoAbrir: YearMonth? = null
    }
}
