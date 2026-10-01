package br.joao.agrosaldo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PrevisaoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.dao.UsuarioDao
import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import br.joao.agrosaldo.data.local.entidade.PrevisaoEntity
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity

/**
 * O banco SQLite do app, gerenciado pelo Room.
 *
 * Fica num arquivo "agrosaldo.db" dentro da pasta privada do app no celular.
 * Outros apps não enxergam, e ele funciona sem internet — é a fonte da
 * verdade dos dados (RNF01).
 *
 * Versões:
 *   1 — propriedades e lançamentos (fase 3)
 *   2 — usuários, e cada propriedade passa a ter dono (login e LGPD)
 *   3 — cache da previsão do tempo (clima e aviso de chuva)
 *
 * Toda vez que uma tabela muda, [version] sobe e entra uma [Migration]
 * dizendo como converter o banco antigo SEM perder dado. O exportSchema gera
 * um JSON por versão em app/schemas — versione essa pasta no Git.
 */
@Database(
    entities = [
        UsuarioEntity::class,
        PropriedadeEntity::class,
        LancamentoEntity::class,
        PrevisaoEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AgroSaldoDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun propriedadeDao(): PropriedadeDao
    abstract fun lancamentoDao(): LancamentoDao
    abstract fun previsaoDao(): PrevisaoDao

    companion object {
        const val NOME_ARQUIVO = "agrosaldo.db"

        /** Quem já tinha o app da fase 3 no celular passa por aqui ao atualizar. */
        val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigracoesSql.DE_1_PARA_2.forEach { sql -> db.execSQL(sql) }
            }
        }

        val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigracoesSql.DE_2_PARA_3.forEach { sql -> db.execSQL(sql) }
            }
        }

        /** Todas, em ordem. Quem está na versão 1 passa pelas duas. */
        val MIGRACOES: Array<Migration> = arrayOf(MIGRACAO_1_2, MIGRACAO_2_3)

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
                // Sem fallbackToDestructiveMigration de propósito: se faltar
                // uma migração, o app avisa com erro em vez de apagar os
                // dados do produtor em silêncio.
                .addMigrations(*MIGRACOES)
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
