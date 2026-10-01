package br.joao.agrosaldo.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import br.joao.agrosaldo.aviso.AgendaWorkManager
import br.joao.agrosaldo.data.clima.OpenMeteoHttp
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.data.preferencias.AjustesAvisoPreferencias
import br.joao.agrosaldo.data.repository.ClimaRepositoryRoom
import br.joao.agrosaldo.data.repository.LancamentoRepositoryRoom
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryRoom
import br.joao.agrosaldo.data.repository.UsuarioRepositoryRoom
import br.joao.agrosaldo.data.sessao.BloqueioPreferencias
import br.joao.agrosaldo.data.sessao.SessaoPreferencias
import br.joao.agrosaldo.data.sessao.TentativasPreferencias
import br.joao.agrosaldo.domain.clima.VerificadorDeChuva
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.BloqueioRepository
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import br.joao.agrosaldo.domain.repository.UsuarioRepository

/**
 * Ponto único de criação dos repositórios — um Service Locator simples.
 *
 * Com a entrada do Room, ele passou a precisar de um Context para abrir o
 * banco. Por isso existe o [inicializar], chamado uma vez pelo
 * [br.joao.agrosaldo.AgroSaldoApp] quando o app sobe.
 *
 * Com o login, entraram a sessão, os usuários e o [conta]. Repare que os
 * repositórios de propriedade e lançamento recebem a sessão: é assim que
 * eles sabem de quem são os dados, sem nenhum ViewModel precisar saber.
 */
object Repositorios {

    @Volatile
    private var banco: AgroSaldoDatabase? = null

    @Volatile
    private var contexto: Context? = null

    fun inicializar(context: Context) {
        if (banco != null) return
        synchronized(this) {
            if (banco != null) return
            // Build de debug (rodando pelo Android Studio) nasce com exemplos.
            // Build de release, o que vai para o produtor, nasce vazio.
            val ehDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            contexto = context.applicationContext
            banco = AgroSaldoDatabase.criar(context, comDadosDeExemplo = ehDebug)
        }
    }

    private val bancoAberto: AgroSaldoDatabase
        get() = checkNotNull(banco) { MENSAGEM_SEM_INICIALIZAR }

    private val contextoAberto: Context
        get() = checkNotNull(contexto) { MENSAGEM_SEM_INICIALIZAR }

    val sessao: SessaoRepository by lazy { SessaoPreferencias(contextoAberto) }

    val bloqueio: BloqueioRepository by lazy { BloqueioPreferencias(contextoAberto) }

    /** Milissegundos desde que o celular ligou. Não muda se alguém mexer na hora do celular. */
    val relogioMonotonico: () -> Long = { SystemClock.elapsedRealtime() }

    val usuarios: UsuarioRepository by lazy {
        UsuarioRepositoryRoom(bancoAberto.usuarioDao())
    }

    val conta: ContaService by lazy {
        ContaService(
            usuarios = usuarios,
            sessao = sessao,
            tentativas = TentativasPreferencias(contextoAberto),
            aoTrocarDeUsuario = SelecaoAtual::limpar
        )
    }

    val propriedades: PropriedadeRepository by lazy {
        PropriedadeRepositoryRoom(bancoAberto.propriedadeDao(), sessao)
    }

    val lancamentos: LancamentoRepository by lazy {
        LancamentoRepositoryRoom(bancoAberto.lancamentoDao(), sessao)
    }

    // ---------- clima e aviso de chuva ----------

    val clima: ClimaRepository by lazy {
        ClimaRepositoryRoom(OpenMeteoHttp(), bancoAberto.previsaoDao())
    }

    val ajustesAviso: AjustesAvisoRepository by lazy { AjustesAvisoPreferencias(contextoAberto) }

    val agendaAviso: AgendaDeVerificacao by lazy { AgendaWorkManager(contextoAberto) }

    val verificadorDeChuva: VerificadorDeChuva by lazy {
        VerificadorDeChuva(sessao, propriedades, clima, ajustesAviso)
    }

    private const val MENSAGEM_SEM_INICIALIZAR =
        "O banco não foi inicializado. Confira se o AndroidManifest.xml tem " +
            "android:name=\".AgroSaldoApp\" na tag <application>."
}
