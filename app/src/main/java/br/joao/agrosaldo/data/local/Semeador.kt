package br.joao.agrosaldo.data.local

import br.joao.agrosaldo.data.DadosDeExemplo
import java.time.LocalDate

/**
 * Monta os INSERTs dos dados de exemplo.
 *
 * Recebe uma função "executar" em vez de um banco. Assim a mesma rotina serve
 * para o SQLite de verdade (no callback do Room) e para os testes, que
 * conseguem conferir exatamente o SQL gerado sem precisar de Android.
 */
object Semeador {

    fun semear(
        executar: (sql: String, argumentos: Array<Any?>) -> Unit,
        hoje: LocalDate = LocalDate.now(),
        agora: Long = System.currentTimeMillis()
    ) {
        // Propriedades antes dos lançamentos: com a chave estrangeira ligada,
        // a ordem contrária seria recusada pelo banco.
        DadosDeExemplo.propriedades().forEach { p ->
            // Sem dono: a primeira conta criada no aparelho fica com elas.
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
