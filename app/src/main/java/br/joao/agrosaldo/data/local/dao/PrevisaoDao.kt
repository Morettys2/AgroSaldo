package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.joao.agrosaldo.data.local.entidade.PrevisaoEntity
import kotlinx.coroutines.flow.Flow

/** DAO (`@Dao`) da tabela `previsoes`: o cache da previsão de cada propriedade. */
@Dao
interface PrevisaoDao {

    /** Observa o cache de uma propriedade (`Flow`; null enquanto não há previsão). */
    @Query("SELECT * FROM previsoes WHERE propriedade_id = :propriedadeId")
    fun observar(propriedadeId: String): Flow<PrevisaoEntity?>

    /** Lê o cache de uma propriedade uma vez. */
    @Query("SELECT * FROM previsoes WHERE propriedade_id = :propriedadeId")
    suspend fun buscar(propriedadeId: String): PrevisaoEntity?

    /** Insere ou substitui o cache da propriedade (`@Upsert`, pela chave `propriedade_id`). */
    @Upsert
    suspend fun salvar(previsao: PrevisaoEntity)

    /** true se a propriedade ainda existe; usado quando gravar o cache viola a chave estrangeira. */
    @Query("SELECT EXISTS(SELECT 1 FROM propriedades WHERE id = :propriedadeId)")
    suspend fun propriedadeExiste(propriedadeId: String): Boolean
}
