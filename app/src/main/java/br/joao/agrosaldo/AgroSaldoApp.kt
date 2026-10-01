package br.joao.agrosaldo

import android.app.Application
import br.joao.agrosaldo.aviso.NotificadorDeChuva
import br.joao.agrosaldo.data.Repositorios

/**
 * Classe que representa o app inteiro. Nasce antes de qualquer tela e vive
 * enquanto o processo estiver de pé.
 *
 * É o lugar certo para preparar o que o app todo usa: o banco, o canal de
 * notificação e o agendamento do aviso de chuva. Fazer isso na MainActivity
 * funcionaria, mas o WorkManager pode acordar o app sem abrir tela nenhuma
 * — e aí só o Application roda.
 *
 * Para o Android usar esta classe, o AndroidManifest precisa apontar para ela:
 *   <application android:name=".AgroSaldoApp" ...>
 */
class AgroSaldoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repositorios.inicializar(this)

        // Criar um canal que já existe não faz nada, então pode rodar sempre.
        NotificadorDeChuva.criarCanal(this)

        // Garante que a verificação está agendada (ou cancelada) conforme o
        // ajuste do produtor. Com KEEP, chamar toda vez não muda o horário.
        if (Repositorios.ajustesAviso.ajustes.value.ativo) {
            Repositorios.agendaAviso.ligar()
        } else {
            Repositorios.agendaAviso.desligar()
        }
    }
}
