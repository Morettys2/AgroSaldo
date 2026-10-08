package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.joao.agrosaldo.data.local.entidade.PrevisaoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrevisaoDao {

    @Query("SELECT * FROM previsoes WHERE propriedade_id = :propriedadeId")
    fun observar(propriedadeId: String): Flow<PrevisaoEntity?>

    @Query("SELECT * FROM previsoes WHERE propriedade_id = :propriedadeId")
    suspend fun buscar(propriedadeId: String): PrevisaoEntity?

    @Upsert
    suspend fun salvar(previsao: PrevisaoEntity)
}
