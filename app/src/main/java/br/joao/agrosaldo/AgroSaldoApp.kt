package br.joao.agrosaldo

import android.app.Application
import br.joao.agrosaldo.aviso.NotificadorDeChuva
import br.joao.agrosaldo.di.Dependencias

/**
 * Ponto de partida do processo: guarda o container de dependências, cria o canal de
 * notificação e sincroniza o agendamento do aviso de chuva. Fica no Application (e não
 * na MainActivity) porque o WorkManager pode subir o processo sem abrir nenhuma Activity.
 * Registrada no manifesto.
 */
class AgroSaldoApp : Application() {

    /**
     * Uma instância por processo, criada sem abrir nada: o banco e os repositórios só
     * nascem no primeiro uso (veja [Dependencias]). Acessada por `context.dependencias`.
     */
    val dependencias: Dependencias by lazy { Dependencias(this) }

    override fun onCreate() {
        super.onCreate()

        // Idempotente: recriar um canal existente não altera nada.
        NotificadorDeChuva.criarCanal(this)

        // Sincroniza o agendamento com o ajuste salvo. Com KEEP, reagendar não muda o horário.
        if (dependencias.ajustesAviso.ajustes.value.ativo) {
            dependencias.agendaAviso.ligar()
        } else {
            dependencias.agendaAviso.desligar()
        }
    }
}
