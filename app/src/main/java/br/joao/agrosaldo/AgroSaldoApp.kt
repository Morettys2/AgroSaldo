package br.joao.agrosaldo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import br.joao.agrosaldo.aviso.NotificadorDeChuva
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Ponto de partida do processo: o Hilt monta o grafo de dependências, e aqui se cria o canal
 * de notificação e se sincroniza o agendamento do aviso de chuva. Fica no Application (e não
 * na MainActivity) porque o WorkManager pode subir o processo sem abrir nenhuma Activity.
 * Registrada no manifesto.
 */
@HiltAndroidApp
class AgroSaldoApp :
    Application(),
    Configuration.Provider {

    @Inject
    lateinit var fabricaDeWorkers: HiltWorkerFactory

    @Inject
    lateinit var ajustesAviso: AjustesAvisoRepository

    @Inject
    lateinit var agendaAviso: AgendaDeVerificacao

    /** O WorkManager cria os Workers pela fábrica do Hilt, que injeta as dependências de cada um. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaDeWorkers).build()

    override fun onCreate() {
        super.onCreate()

        // Idempotente: recriar um canal existente não altera nada.
        NotificadorDeChuva.criarCanal(this)

        // Sincroniza o agendamento com o ajuste salvo. Com KEEP, reagendar não muda o horário.
        if (ajustesAviso.ajustes.value.ativo) {
            agendaAviso.ligar()
        } else {
            agendaAviso.desligar()
        }
    }
}
