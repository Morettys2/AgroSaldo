package br.joao.agrosaldo

import android.app.Application
import br.joao.agrosaldo.aviso.NotificadorDeChuva
import br.joao.agrosaldo.data.Repositorios

/**
 * Inicializa dependências globais: repositórios, canal de notificação e agendamento
 * do aviso de chuva. Fica no Application (e não na MainActivity) porque o WorkManager
 * pode subir o processo sem abrir nenhuma Activity. Registrada no manifesto.
 */
class AgroSaldoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repositorios.inicializar(this)

        // Idempotente: recriar um canal existente não altera nada.
        NotificadorDeChuva.criarCanal(this)

        // Sincroniza o agendamento com o ajuste salvo. Com KEEP, reagendar não muda o horário.
        if (Repositorios.ajustesAviso.ajustes.value.ativo) {
            Repositorios.agendaAviso.ligar()
        } else {
            Repositorios.agendaAviso.desligar()
        }
    }
}
