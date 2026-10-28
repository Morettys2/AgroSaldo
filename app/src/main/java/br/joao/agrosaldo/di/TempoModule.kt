package br.joao.agrosaldo.di

import android.content.Context
import br.joao.agrosaldo.data.RelogioDoAparelho
import br.joao.agrosaldo.data.RelogioMonotonicoDoAndroid
import br.joao.agrosaldo.domain.Despachantes
import br.joao.agrosaldo.domain.DespachantesDoSistema
import br.joao.agrosaldo.domain.RelogioMonotonico
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** O que o app usa para saber que horas são e onde rodar cada trabalho. */
@Module
@InstallIn(SingletonComponent::class)
object TempoModule {

    @Provides
    @Singleton
    fun relogio(): Clock = RelogioDoAparelho

    @Provides
    @Singleton
    fun relogioMonotonico(@ApplicationContext contexto: Context): RelogioMonotonico = RelogioMonotonicoDoAndroid(contexto)

    @Provides
    @Singleton
    fun despachantes(): Despachantes = DespachantesDoSistema

    /**
     * Coleta de longa duração (ex.: ajustes que acompanham a sessão). Unconfined: reage na thread que
     * mudou. É o único `Dispatchers.*` fora de [DespachantesDoSistema]: este módulo é a raiz que monta
     * as dependências, e os testes não usam este escopo (passam o deles).
     */
    @Provides
    @Singleton
    @EscopoDoApp
    fun escopoDoApp(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
}
