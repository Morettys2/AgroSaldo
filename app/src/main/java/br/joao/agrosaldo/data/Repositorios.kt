package br.joao.agrosaldo.data

import android.app.backup.BackupManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteException
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.room.withTransaction
import br.joao.agrosaldo.aviso.AgendaWorkManager
import br.joao.agrosaldo.data.clima.OpenMeteoHttp
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.data.local.Transacao
import br.joao.agrosaldo.data.preferencias.AjustesAvisoPreferencias
import br.joao.agrosaldo.data.preferencias.PrivacidadePreferencias
import br.joao.agrosaldo.data.repository.ClimaRepositoryRoom
import br.joao.agrosaldo.data.repository.LancamentoRepositoryRoom
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryRoom
import br.joao.agrosaldo.data.repository.UsuarioRepositoryRoom
import br.joao.agrosaldo.data.sessao.BloqueioPreferencias
import br.joao.agrosaldo.data.sessao.SessaoPreferencias
import br.joao.agrosaldo.data.sessao.TentativasPreferencias
import br.joao.agrosaldo.domain.clima.VerificadorDeChuva
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.model.InstanteMonotonico
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.BloqueioRepository
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PrivacidadeRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service locator das dependências do app.
 *
 * [inicializar] deve ser chamado em [br.joao.agrosaldo.AgroSaldoApp.onCreate]
 * antes de qualquer acesso. Repositórios de propriedade e lançamento recebem a
 * sessão para filtrar os dados pelo usuário conectado.
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
            // Só builds debuggable semeiam dados de exemplo.
            val ehDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            contexto = context.applicationContext
            banco = AgroSaldoDatabase.criar(context, comDadosDeExemplo = ehDebug)
            lembrarPropriedadeEscolhida(context.applicationContext)
        }
    }

    /** Persiste em SharedPreferences o id da propriedade selecionada (UUID, sem dado pessoal). */
    private fun lembrarPropriedadeEscolhida(context: Context) {
        val preferencias = context.getSharedPreferences(ARQUIVO_SELECAO, Context.MODE_PRIVATE)
        SelecaoAtual.configurar(inicial = preferencias.getString(CHAVE_PROPRIEDADE, null)) { id ->
            preferencias.edit {
                if (id == null) remove(CHAVE_PROPRIEDADE) else putString(CHAVE_PROPRIEDADE, id)
            }
        }
    }

    private val bancoAberto: AgroSaldoDatabase
        get() = checkNotNull(banco) { MENSAGEM_SEM_INICIALIZAR }

    private val contextoAberto: Context
        get() = checkNotNull(contexto) { MENSAGEM_SEM_INICIALIZAR }

    val sessao: SessaoRepository by lazy { SessaoPreferencias(contextoAberto) }

    val bloqueio: BloqueioRepository by lazy { BloqueioPreferencias(contextoAberto) }

    /** elapsedRealtime em ms: imune a mudanças no relógio do sistema. */
    val relogioMonotonico: () -> Long = { SystemClock.elapsedRealtime() }

    /** Contagem de boots do aparelho; constante durante a vida do processo. null se o sistema não informa. */
    private val bootAtual: Int? by lazy {
        Settings.Global.getInt(contextoAberto.contentResolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }
    }

    /** [relogioMonotonico] com o boot atual, para comparar instantes gravados em disco. */
    private val instanteMonotonico: () -> InstanteMonotonico? = {
        bootAtual?.let { InstanteMonotonico(it, SystemClock.elapsedRealtime()) }
    }

    val usuarios: UsuarioRepository by lazy {
        UsuarioRepositoryRoom(bancoAberto.usuarioDao())
    }

    val conta: ContaService by lazy {
        ContaService(
            usuarios = usuarios,
            sessao = sessao,
            tentativas = TentativasPreferencias(contextoAberto),
            relogioMonotonico = instanteMonotonico,
            // Limpa estado da conta anterior: seleção, desfazer pendente e
            // notificações (contêm nomes de propriedades).
            aoTrocarDeUsuario = {
                SelecaoAtual.limpar()
                desfazer.esquecer()
                NotificationManagerCompat.from(contextoAberto).cancelAll()
            },
            aoExcluirConta = { usuarioId -> apagarRastrosDaConta(usuarioId) }
        )
    }

    /**
     * O que a conta excluída deixa fora das tabelas: os ajustes do aparelho que ela
     * definiu (valores ocultos e bloqueio por digital voltam ao padrão), os ajustes do
     * aviso de chuva e as páginas livres do SQLite, que guardam os registros apagados
     * até o VACUUM. Avisa o backup do Android para a próxima cópia já sair sem a conta.
     */
    private suspend fun apagarRastrosDaConta(usuarioId: String) {
        privacidade.definir(false)
        bloqueio.definir(false)
        ajustesAviso.excluirDadosDoUsuario(usuarioId)
        BackupManager(contextoAberto).dataChanged()
        compactarBanco()
    }

    /**
     * VACUUM reescreve o banco sem as páginas livres; o checkpoint copia o resultado do
     * WAL para o arquivo e esvazia o WAL. Precisa rodar fora de transação.
     */
    private suspend fun compactarBanco() {
        withContext(Dispatchers.IO) {
            try {
                val banco = bancoAberto.openHelper.writableDatabase
                banco.execSQL("VACUUM")
                banco.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            } catch (e: SQLiteException) {
                // Os registros já foram apagados; sem espaço para o VACUUM, as páginas livres
                // são reaproveitadas pelas próximas gravações.
            }
        }
    }

    val propriedades: PropriedadeRepository by lazy {
        PropriedadeRepositoryRoom(
            dao = bancoAberto.propriedadeDao(),
            lancamentoDao = bancoAberto.lancamentoDao(),
            sessao = sessao,
            transacao = object : Transacao {
                override suspend fun <R> executar(bloco: suspend () -> R): R =
                    bancoAberto.withTransaction { bloco() }
            }
        )
    }

    val lancamentos: LancamentoRepository by lazy {
        LancamentoRepositoryRoom(bancoAberto.lancamentoDao(), sessao)
    }

    // Clima e aviso de chuva

    val clima: ClimaRepository by lazy {
        ClimaRepositoryRoom(OpenMeteoHttp(), bancoAberto.previsaoDao())
    }

    val ajustesAviso: AjustesAvisoRepository by lazy { AjustesAvisoPreferencias(contextoAberto) }

    val agendaAviso: AgendaDeVerificacao by lazy { AgendaWorkManager(contextoAberto) }

    val verificadorDeChuva: VerificadorDeChuva by lazy {
        VerificadorDeChuva(sessao, propriedades, clima, ajustesAviso)
    }

    val privacidade: PrivacidadeRepository by lazy { PrivacidadePreferencias(contextoAberto) }

    val desfazer: Desfazer by lazy { Desfazer(relogioMonotonico) }

    private const val ARQUIVO_SELECAO = "selecao"
    private const val CHAVE_PROPRIEDADE = "propriedade_id"

    private const val MENSAGEM_SEM_INICIALIZAR =
        "O banco não foi inicializado. Confira se o AndroidManifest.xml tem " +
            "android:name=\".AgroSaldoApp\" na tag <application>."
}
