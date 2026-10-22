package br.joao.agrosaldo.di

import android.content.Context
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.data.local.Transacao
import br.joao.agrosaldo.data.local.TransacaoRoom
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PrevisaoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.dao.UsuarioDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo do Hilt (`@InstallIn(SingletonComponent::class)`) que fornece o banco, os DAOs e a
 * [Transacao]. O banco é aberto na primeira injeção.
 */
@Module
@InstallIn(SingletonComponent::class)
object BancoModule {

    /** Instância única do banco no app (`@Singleton`). */
    @Provides
    @Singleton
    fun banco(@ApplicationContext contexto: Context): AgroSaldoDatabase = AgroSaldoDatabase.criar(contexto)

    /** Os DAOs vêm do banco único. */
    @Provides
    fun usuarioDao(banco: AgroSaldoDatabase): UsuarioDao = banco.usuarioDao()

    @Provides
    fun propriedadeDao(banco: AgroSaldoDatabase): PropriedadeDao = banco.propriedadeDao()

    @Provides
    fun lancamentoDao(banco: AgroSaldoDatabase): LancamentoDao = banco.lancamentoDao()

    @Provides
    fun previsaoDao(banco: AgroSaldoDatabase): PrevisaoDao = banco.previsaoDao()

    /** Transação real, para os repositórios que gravam em mais de uma tabela. */
    @Provides
    @Singleton
    fun transacao(banco: AgroSaldoDatabase): Transacao = TransacaoRoom(banco)
}
