package br.joao.agrosaldo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity

/**
 * O banco SQLite do app, gerenciado pelo Room.
 *
 * Fica num arquivo "agrosaldo.db" dentro da pasta privada do app no celular.
 * Outros apps não enxergam, e ele funciona sem internet — é a fonte da
 * verdade dos dados (RNF01).
 *
 * version = 1: toda vez que uma tabela mudar depois que o app estiver em uso,
 * este número sobe e é preciso escrever uma Migration dizendo como converter
 * o banco antigo. O exportSchema gera um JSON em app/schemas com o formato
 * de cada versão — versione essa pasta no Git, é ela que permite comparar.
 */
@Database(
    entities = [PropriedadeEntity::class, LancamentoEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AgroSaldoDatabase : RoomDatabase() {

    abstract fun propriedadeDao(): PropriedadeDao
    abstract fun lancamentoDao(): LancamentoDao

    companion object {
        const val NOME_ARQUIVO = "agrosaldo.db"

        /**
         * Cria o banco. Deve ser chamado uma vez só no app inteiro — abrir o
         * mesmo arquivo SQLite por duas instâncias causa conflito de escrita.
         * Quem garante a instância única é o [br.joao.agrosaldo.data.Repositorios].
         *
         * [comDadosDeExemplo]: quando true, o banco nasce com as duas
         * propriedades e os lançamentos de exemplo.
         */
        fun criar(context: Context, comDadosDeExemplo: Boolean): AgroSaldoDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AgroSaldoDatabase::class.java,
                NOME_ARQUIVO
            )
                .addCallback(object : Callback() {
                    /**
                     * Roda UMA vez: quando o arquivo do banco é criado.
                     * Não roda de novo nas próximas aberturas — então, se o
                     * usuário apagar tudo, os exemplos não voltam sozinhos.
                     */
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        if (comDadosDeExemplo) {
                            Semeador.semear(executar = { sql, argumentos -> db.execSQL(sql, argumentos) })
                        }
                    }
                })
                .build()
    }
}
