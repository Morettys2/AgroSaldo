package br.joao.agrosaldo.data

import android.content.Context
import android.content.pm.ApplicationInfo
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.data.repository.LancamentoRepositoryRoom
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryRoom
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository

/**
 * Ponto único de criação dos repositórios — um Service Locator simples.
 *
 * Com a entrada do Room, ele passou a precisar de um Context para abrir o
 * banco. Por isso existe o [inicializar], chamado uma vez pelo
 * [br.joao.agrosaldo.AgroSaldoApp] quando o app sobe.
 *
 * A troca de "memória" para "Room" aconteceu inteira neste arquivo: as duas
 * linhas de baixo passaram a criar as versões Room. ViewModels e telas não
 * foram tocados — é o ganho concreto de depender de interfaces.
 */
object Repositorios {

    @Volatile
    private var banco: AgroSaldoDatabase? = null

    fun inicializar(context: Context) {
        if (banco != null) return
        synchronized(this) {
            if (banco != null) return
            // Build de debug (rodando pelo Android Studio) nasce com exemplos.
            // Build de release, o que vai para o produtor, nasce vazio.
            val ehDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            banco = AgroSaldoDatabase.criar(context, comDadosDeExemplo = ehDebug)
        }
    }

    private val bancoAberto: AgroSaldoDatabase
        get() = checkNotNull(banco) {
            "O banco não foi inicializado. Confira se o AndroidManifest.xml tem " +
                "android:name=\".AgroSaldoApp\" na tag <application>."
        }

    val propriedades: PropriedadeRepository by lazy {
        PropriedadeRepositoryRoom(bancoAberto.propriedadeDao())
    }

    val lancamentos: LancamentoRepository by lazy {
        LancamentoRepositoryRoom(bancoAberto.lancamentoDao())
    }
}
