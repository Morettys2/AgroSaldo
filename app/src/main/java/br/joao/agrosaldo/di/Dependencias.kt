package br.joao.agrosaldo.di

import android.app.backup.BackupManager
import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.withTransaction
import br.joao.agrosaldo.AgroSaldoApp
import br.joao.agrosaldo.aviso.AgendaWorkManager
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.SelecaoAtual
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
 * Container de dependências do app (injeção de dependência manual).
 *
 * Cria, UMA vez, o banco, os repositórios e os serviços, e liga uns aos outros. Os
 * ViewModels não conhecem este container: recebem só o que usam no construtor, e quem
 * faz a ligação são as `Factory` de cada ViewModel, que leem a instância guardada pelo
 * [AgroSaldoApp] (veja [dependencias]).
 *
 * É o padrão do guia oficial "Manual dependency injection" do Android. Um framework como
 * o Hilt faria o mesmo gerando este código; num app deste tamanho, escrever à mão deixa
 * claro de onde vem cada objeto e não acrescenta nenhuma biblioteca.
 *
 * Tudo é `by lazy`: um objeto só é criado quando alguém pede, e o processo subido pelo
 * WorkManager para checar a chuva não abre o que as telas usam.
 */
class Dependencias(contexto: Context) {

    private val contextoAberto: Context = contexto.applicationContext

    /** Banco aberto na primeira consulta, sem dados de exemplo: o app começa vazio. */
    private val bancoAberto: AgroSaldoDatabase by lazy { AgroSaldoDatabase.criar(contextoAberto) }

    /** Propriedade e mês escolhidos, compartilhados entre as telas; a propriedade fica gravada. */
    val selecao: SelecaoAtual by lazy {
        val preferencias = contextoAberto.getSharedPreferences(ARQUIVO_SELECAO, Context.MODE_PRIVATE)
        // Só o id da propriedade (UUID, sem dado pessoal).
        SelecaoAtual(
            propriedadeInicial = preferencias.getString(CHAVE_PROPRIEDADE, null),
            aoMudarPropriedade = { id ->
                preferencias.edit {
                    if (id == null) remove(CHAVE_PROPRIEDADE) else putString(CHAVE_PROPRIEDADE, id)
                }
            }
        )
    }

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

    /** Transação do Room para repositórios que combinam várias escritas e checagens. */
    private val transacaoDoBanco: Transacao = object : Transacao {
        override suspend fun <R> executar(bloco: suspend () -> R): R = bancoAberto.withTransaction { bloco() }
    }

    val usuarios: UsuarioRepository by lazy {
        UsuarioRepositoryRoom(
            dao = bancoAberto.usuarioDao(),
            transacao = transacaoDoBanco
        )
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
                selecao.limpar()
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
            transacao = transacaoDoBanco
        )
    }

    val lancamentos: LancamentoRepository by lazy {
        LancamentoRepositoryRoom(bancoAberto.lancamentoDao(), sessao, transacaoDoBanco)
    }

    // Clima e aviso de chuva

    val clima: ClimaRepository by lazy {
        ClimaRepositoryRoom(OpenMeteoHttp(), bancoAberto.previsaoDao())
    }

    /** Ajustes por conta; ao entrar em outra conta, o agendamento passa a seguir o ajuste dela. */
    val ajustesAviso: AjustesAvisoRepository by lazy {
        AjustesAvisoPreferencias(contextoAberto, sessao) { if (it.ativo) agendaAviso.ligar() else agendaAviso.desligar() }
    }

    val agendaAviso: AgendaDeVerificacao by lazy { AgendaWorkManager(contextoAberto) }

    val verificadorDeChuva: VerificadorDeChuva by lazy {
        VerificadorDeChuva(sessao, propriedades, clima, ajustesAviso)
    }

    val privacidade: PrivacidadeRepository by lazy { PrivacidadePreferencias(contextoAberto) }

    val desfazer: Desfazer by lazy { Desfazer(relogioMonotonico) }

    private companion object {
        const val ARQUIVO_SELECAO = "selecao"
        const val CHAVE_PROPRIEDADE = "propriedade_id"
    }
}

/**
 * O container do app, a partir de qualquer Context (Activity, Worker, Composable).
 * Falha com uma mensagem clara se o manifesto não registrar o [AgroSaldoApp].
 */
val Context.dependencias: Dependencias
    get() {
        val app = applicationContext as? AgroSaldoApp
            ?: error("O AndroidManifest.xml precisa de android:name=\".AgroSaldoApp\" na tag <application>.")
        return app.dependencias
    }

/** O container dentro de `viewModelFactory { initializer { ... } }`. */
val CreationExtras.dependencias: Dependencias
    get() = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
        "A Factory precisa ser usada por viewModel(), que fornece a Application."
    }.dependencias
