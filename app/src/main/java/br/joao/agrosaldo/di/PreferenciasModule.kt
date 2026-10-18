package br.joao.agrosaldo.di

import android.content.Context
import androidx.core.content.edit
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.preferencias.AjustesAvisoPreferencias
import br.joao.agrosaldo.data.preferencias.PrivacidadePreferencias
import br.joao.agrosaldo.data.sessao.BloqueioPreferencias
import br.joao.agrosaldo.data.sessao.SessaoPreferencias
import br.joao.agrosaldo.data.sessao.TentativasPreferencias
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.BloqueioRepository
import br.joao.agrosaldo.domain.repository.PrivacidadeRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import br.joao.agrosaldo.domain.repository.TentativasRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** O que o app guarda em SharedPreferences: sessão, bloqueio, privacidade, ajustes do aviso e a seleção atual. */
@Module
@InstallIn(SingletonComponent::class)
object PreferenciasModule {

    private const val ARQUIVO_SELECAO = "selecao"
    private const val CHAVE_PROPRIEDADE = "propriedade_id"

    @Provides
    @Singleton
    fun sessao(@ApplicationContext contexto: Context): SessaoRepository = SessaoPreferencias(contexto)

    @Provides
    @Singleton
    fun bloqueio(@ApplicationContext contexto: Context): BloqueioRepository = BloqueioPreferencias(contexto)

    @Provides
    @Singleton
    fun tentativas(@ApplicationContext contexto: Context): TentativasRepository = TentativasPreferencias(contexto)

    @Provides
    @Singleton
    fun privacidade(@ApplicationContext contexto: Context): PrivacidadeRepository = PrivacidadePreferencias(contexto)

    /** Ajustes por conta; ao entrar em outra conta, o agendamento passa a seguir o ajuste dela. */
    @Provides
    @Singleton
    fun ajustesAviso(
        @ApplicationContext contexto: Context,
        sessao: SessaoRepository,
        agenda: AgendaDeVerificacao,
        @EscopoDoApp escopo: CoroutineScope
    ): AjustesAvisoRepository = AjustesAvisoPreferencias(contexto, sessao, escopo = escopo) { ajustes ->
        if (ajustes.ativo) agenda.ligar() else agenda.desligar()
    }

    /** Propriedade e mês escolhidos, compartilhados entre as telas; a propriedade fica gravada. */
    @Provides
    @Singleton
    fun selecao(@ApplicationContext contexto: Context, relogio: Clock): SelecaoAtual {
        val preferencias = contexto.getSharedPreferences(ARQUIVO_SELECAO, Context.MODE_PRIVATE)
        // Só o id da propriedade (UUID, sem dado pessoal).
        return SelecaoAtual(
            propriedadeInicial = preferencias.getString(CHAVE_PROPRIEDADE, null),
            aoMudarPropriedade = { id ->
                preferencias.edit {
                    if (id == null) remove(CHAVE_PROPRIEDADE) else putString(CHAVE_PROPRIEDADE, id)
                }
            },
            relogio = relogio
        )
    }
}
