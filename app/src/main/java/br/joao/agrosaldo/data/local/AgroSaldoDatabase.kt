package br.joao.agrosaldo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
 * Versão 3 é o ponto de partida da produção: usuários, propriedades, lançamentos
 * e cache de previsão. Toda mudança de schema exige incrementar a versão e
 * escrever uma Migration sem perda de dados (schemas exportados em app/schemas).
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
            .build()
    }
}
