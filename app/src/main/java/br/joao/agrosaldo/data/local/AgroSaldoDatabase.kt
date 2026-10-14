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
 * Banco Room local ([NOME_ARQUIVO], armazenamento privado do app); fonte da
 * verdade offline (RNF01).
 *
 * Histórico de versões:
 * - 1: propriedades e lançamentos.
 * - 2: usuários; propriedades ganham dono (`usuario_id`).
 * - 3: cache de previsão do tempo (`previsoes`).
 *
 * Toda mudança de schema exige incrementar a versão e adicionar uma [Migration]
 * sem perda de dados. Os schemas exportados ficam em app/schemas (versionados).
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

        /** Todas as migrações, em ordem. */
        val MIGRACOES: Array<Migration> = arrayOf(MIGRACAO_1_2, MIGRACAO_2_3)

        /**
         * Abre o banco, sempre vazio na primeira vez: nenhum dado de exemplo é gravado.
         * Deve existir uma única instância por processo (garantida por
         * [br.joao.agrosaldo.di.Dependencias]).
         */
        fun criar(context: Context): AgroSaldoDatabase = Room.databaseBuilder(
            context.applicationContext,
            AgroSaldoDatabase::class.java,
            NOME_ARQUIVO
        )
            // Sem fallbackToDestructiveMigration: migração ausente deve falhar,
            // nunca apagar dados do usuário.
            .addMigrations(*MIGRACOES)
            .build()
    }
}
