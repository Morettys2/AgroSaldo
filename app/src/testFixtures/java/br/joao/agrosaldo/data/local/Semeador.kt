package br.joao.agrosaldo.data.local

import br.joao.agrosaldo.data.DadosDeExemplo
import java.time.LocalDate

/**
 * Gera os INSERTs de [DadosDeExemplo], com as propriedades da conta [usuarioId] (o dono é
 * obrigatório desde a versão 4 do banco). Recebe a função `executar` em vez do banco para ser
 * usado tanto com o SQLite de verdade quanto nos testes JVM.
 */
object Semeador {

    fun semear(
        executar: (sql: String, argumentos: Array<Any?>) -> Unit,
        usuarioId: String,
        hoje: LocalDate = LocalDate.now(),
        agora: Long = System.currentTimeMillis()
    ) {
        // Propriedades antes dos lançamentos por causa da foreign key.
        DadosDeExemplo.propriedades().forEach { p ->
            val e = p.paraEntidade(usuarioId = usuarioId, agora = agora)
            executar(
                "INSERT INTO propriedades " +
                    "(id, nome, cultura, area_hectares, latitude, longitude, atualizado_em, usuario_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(e.id, e.nome, e.cultura, e.areaHectares, e.latitude, e.longitude, e.atualizadoEm, e.usuarioId)
            )
        }

        DadosDeExemplo.lancamentos(hoje).forEach { l ->
            val e = l.paraEntidade(agora)
            executar(
                "INSERT INTO lancamentos " +
                    "(id, propriedade_id, tipo, categoria, valor_centavos, data, descricao, atualizado_em) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    e.id,
                    e.propriedadeId,
                    e.tipo,
                    e.categoria,
                    e.valorCentavos,
                    e.data,
                    e.descricao,
                    e.atualizadoEm
                )
            )
        }
    }
}
