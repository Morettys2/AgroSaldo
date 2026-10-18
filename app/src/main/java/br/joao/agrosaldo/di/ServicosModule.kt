package br.joao.agrosaldo.di

import android.content.Context
import br.joao.agrosaldo.aviso.AgendaWorkManager
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.LimpezaDaConta
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.domain.Despachantes
import br.joao.agrosaldo.domain.RelogioMonotonico
import br.joao.agrosaldo.domain.clima.VerificadorDeChuva
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.BloqueioRepository
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.PrivacidadeRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import br.joao.agrosaldo.domain.repository.TentativasRepository
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/** Serviços do domínio (contas e verificação de chuva) e o agendamento em segundo plano. */
@Module
@InstallIn(SingletonComponent::class)
object ServicosModule {

    @Provides
    @Singleton
    fun agendaAviso(@ApplicationContext contexto: Context): AgendaDeVerificacao = AgendaWorkManager(contexto)

    @Provides
    @Singleton
    fun limpezaDaConta(
        @ApplicationContext contexto: Context,
        selecao: SelecaoAtual,
        desfazer: Desfazer,
        privacidade: PrivacidadeRepository,
        bloqueio: BloqueioRepository,
        ajustesAviso: AjustesAvisoRepository,
        banco: AgroSaldoDatabase,
        despachantes: Despachantes
    ): LimpezaDaConta = LimpezaDaConta(contexto, selecao, desfazer, privacidade, bloqueio, ajustesAviso, banco, despachantes)

    @Provides
    @Singleton
    fun conta(
        usuarios: UsuarioRepository,
        sessao: SessaoRepository,
        tentativas: TentativasRepository,
        relogio: Clock,
        relogioMonotonico: RelogioMonotonico,
        despachantes: Despachantes,
        limpeza: LimpezaDaConta
    ): ContaService = ContaService(
        usuarios = usuarios,
        sessao = sessao,
        tentativas = tentativas,
        relogio = relogio,
        relogioMonotonico = relogioMonotonico,
        despachantes = despachantes,
        aoTrocarDeUsuario = limpeza::limparEstadoDaContaAnterior,
        aoExcluirConta = limpeza::apagarRastrosDaConta
    )

    @Provides
    @Singleton
    fun verificadorDeChuva(
        sessao: SessaoRepository,
        propriedades: PropriedadeRepository,
        clima: ClimaRepository,
        ajustes: AjustesAvisoRepository,
        relogio: Clock
    ): VerificadorDeChuva = VerificadorDeChuva(sessao, propriedades, clima, ajustes, relogio)
}
