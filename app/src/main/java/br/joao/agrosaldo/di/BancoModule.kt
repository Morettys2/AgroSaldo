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
 * O banco Room e os DAOs. O banco é aberto na primeira consulta e começa sempre vazio:
 * nenhum dado de exemplo é gravado.
 */
@Module
@InstallIn(SingletonComponent::class)
object BancoModule {

    @Provides
    @Singleton
    fun banco(@ApplicationContext contexto: Context): AgroSaldoDatabase = AgroSaldoDatabase.criar(contexto)

    @Provides
    fun usuarioDao(banco: AgroSaldoDatabase): UsuarioDao = banco.usuarioDao()

    @Provides
    fun propriedadeDao(banco: AgroSaldoDatabase): PropriedadeDao = banco.propriedadeDao()

    @Provides
    fun lancamentoDao(banco: AgroSaldoDatabase): LancamentoDao = banco.lancamentoDao()

    @Provides
    fun previsaoDao(banco: AgroSaldoDatabase): PrevisaoDao = banco.previsaoDao()

    /** Transação do Room para repositórios que combinam várias escritas e checagens. */
    @Provides
    @Singleton
    fun transacao(banco: AgroSaldoDatabase): Transacao = TransacaoRoom(banco)
}
