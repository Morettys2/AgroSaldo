package br.joao.agrosaldo.data.local

import br.joao.agrosaldo.data.DadosDeExemplo
import java.time.LocalDate

/**
 * Gera os INSERTs de [DadosDeExemplo]. Recebe a função `executar` em vez do
 * banco para ser usado tanto nos testes instrumentados (SQLite de verdade, inclusive
 * nas migrações) quanto nos testes JVM.
 */
object Semeador {

    fun semear(
        executar: (sql: String, argumentos: Array<Any?>) -> Unit,
        hoje: LocalDate = LocalDate.now(),
        agora: Long = System.currentTimeMillis()
    ) {
        // Propriedades antes dos lançamentos por causa da foreign key.
        DadosDeExemplo.propriedades().forEach { p ->
            // Sem dono: adotadas pela primeira conta criada no aparelho.
            val e = p.paraEntidade(usuarioId = null, agora = agora)
            executar(
                "INSERT INTO propriedades " +
                    "(id, nome, cultura, area_hectares, latitude, longitude, atualizado_em) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf(e.id, e.nome, e.cultura, e.areaHectares, e.latitude, e.longitude, e.atualizadoEm)
            )
        }

        DadosDeExemplo.lancamentos(hoje).forEach { l ->
            val e = l.paraEntidade(agora)
            executar(
                "INSERT INTO lancamentos " +
                    "(id, propriedade_id, tipo, categoria, valor_centavos, data, descricao, atualizado_em) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    e.id, e.propriedadeId, e.tipo, e.categoria,
                    e.valorCentavos, e.data, e.descricao, e.atualizadoEm
                )
            )
        }
    }
}
