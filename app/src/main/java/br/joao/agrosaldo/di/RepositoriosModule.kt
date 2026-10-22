package br.joao.agrosaldo.di

import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.clima.FontePrevisao
import br.joao.agrosaldo.data.clima.OpenMeteoHttp
import br.joao.agrosaldo.data.local.Transacao
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PrevisaoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.dao.UsuarioDao
import br.joao.agrosaldo.data.repository.ClimaRepositoryRoom
import br.joao.agrosaldo.data.repository.LancamentoRepositoryRoom
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryRoom
import br.joao.agrosaldo.data.repository.UsuarioLogadoRoom
import br.joao.agrosaldo.data.repository.UsuarioRepositoryRoom
import br.joao.agrosaldo.domain.Despachantes
import br.joao.agrosaldo.domain.RelogioMonotonico
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import br.joao.agrosaldo.domain.repository.UsuarioLogadoRepository
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Módulo do Hilt que liga cada interface de repositório do domínio à sua implementação (Room e
 * Open-Meteo). Os ViewModels recebem as interfaces pelo construtor.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoriosModule {

    @Provides
    @Singleton
    fun usuarios(dao: UsuarioDao, transacao: Transacao): UsuarioRepository = UsuarioRepositoryRoom(dao, transacao)

    @Provides
    @Singleton
    fun usuarioLogado(sessao: SessaoRepository, usuarios: UsuarioRepository): UsuarioLogadoRepository = UsuarioLogadoRoom(sessao, usuarios)

    @Provides
    @Singleton
    fun propriedades(
        dao: PropriedadeDao,
        lancamentoDao: LancamentoDao,
        sessao: SessaoRepository,
        transacao: Transacao,
        relogio: Clock
    ): PropriedadeRepository = PropriedadeRepositoryRoom(dao, lancamentoDao, sessao, transacao, relogio)

    @Provides
    @Singleton
    fun lancamentos(dao: LancamentoDao, sessao: SessaoRepository, transacao: Transacao, relogio: Clock): LancamentoRepository =
        LancamentoRepositoryRoom(dao, sessao, transacao, relogio)

    /** Fonte da previsão: GET HTTPS na Open-Meteo ([OpenMeteoHttp]). */
    @Provides
    @Singleton
    fun fontePrevisao(despachantes: Despachantes): FontePrevisao = OpenMeteoHttp(despachantes)

    /** Previsão da Open-Meteo guardada no Room (cache primeiro; a rede só renova). */
    @Provides
    @Singleton
    fun clima(fonte: FontePrevisao, dao: PrevisaoDao, despachantes: Despachantes, relogio: Clock): ClimaRepository =
        ClimaRepositoryRoom(fonte, dao, despachantes, relogio)

    /** Desfazer de exclusões, compartilhado entre telas; a validade da oferta usa o relógio monotônico. */
    @Provides
    @Singleton
    fun desfazer(relogio: RelogioMonotonico): Desfazer = Desfazer(relogio)
}
