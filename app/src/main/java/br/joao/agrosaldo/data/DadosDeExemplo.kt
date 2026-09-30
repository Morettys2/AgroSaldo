package br.joao.agrosaldo.data

import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import java.time.LocalDate
import java.time.YearMonth

/**
 * Dados de exemplo usados em dois lugares:
 *
 *  - na primeira abertura do app em modo debug, para o banco não nascer vazio
 *    e a apresentação ter o que mostrar;
 *  - nos testes automatizados, como massa de dados conhecida.
 *
 * Ficar num lugar só garante que o que você vê no emulador é exatamente o
 * que os testes verificam.
 *
 * Em build de release o banco nasce vazio: o produtor começa do zero.
 */
object DadosDeExemplo {

    const val ID_FAZENDA = "exemplo-fazenda-sao-joao"
    const val ID_SITIO = "exemplo-sitio-boa-vista"

    fun propriedades(): List<Propriedade> = listOf(
        Propriedade(
            id = ID_FAZENDA,
            nome = "Fazenda São João da Serra",
            cultura = "Milho",
            areaHectares = 50.0,
            latitude = -21.7333,
            longitude = -46.9667
        ),
        Propriedade(
            id = ID_SITIO,
            nome = "Sítio Boa Vista",
            cultura = "Soja",
            areaHectares = 18.0,
            latitude = -21.8012,
            longitude = -46.9021
        )
    )

    /**
     * Lançamentos no mês de [hoje] e no anterior, para a navegação entre meses
     * (RF10) ter o que mostrar.
     *
     * [hoje] é parâmetro, e não LocalDate.now() direto, para os testes
     * conseguirem fixar uma data e ter resultado previsível.
     */
    fun lancamentos(hoje: LocalDate = LocalDate.now()): List<Lancamento> {
        val mesAtual = YearMonth.from(hoje)
        val mesPassado = mesAtual.minusMonths(1)

        // No mês corrente nenhum exemplo fica depois de hoje — senão a tela
        // mostraria "últimos lançamentos" com data que ainda nem chegou.
        fun dia(mes: YearMonth, diaDoMes: Int): LocalDate {
            val limite = if (mes == mesAtual) hoje.dayOfMonth else mes.lengthOfMonth()
            return mes.atDay(minOf(diaDoMes, limite))
        }

        return listOf(
            Lancamento(
                id = "exemplo-1", propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.RECEITA, categoria = CategoriaLancamento.VENDA_SAFRA,
                valorCentavos = 1_800_000L, data = dia(mesAtual, 20), descricao = "Venda de milho"
            ),
            Lancamento(
                id = "exemplo-2", propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.ENERGIA,
                valorCentavos = 125_000L, data = dia(mesAtual, 26), descricao = "Conta do pivô"
            ),
            Lancamento(
                id = "exemplo-3", propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.ADUBO,
                valorCentavos = 78_000L, data = dia(mesAtual, 12)
            ),
            Lancamento(
                id = "exemplo-4", propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.COMBUSTIVEL,
                valorCentavos = 32_000L, data = dia(mesAtual, 28), descricao = "Abastecimento do trator"
            ),
            Lancamento(
                id = "exemplo-5", propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.MAO_DE_OBRA,
                valorCentavos = 210_000L, data = dia(mesPassado, 22), descricao = "Diarista da colheita"
            ),
            Lancamento(
                id = "exemplo-6", propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.RECEITA, categoria = CategoriaLancamento.VENDA_SAFRA,
                valorCentavos = 940_000L, data = dia(mesPassado, 14)
            ),
            Lancamento(
                id = "exemplo-7", propriedadeId = ID_SITIO,
                tipo = TipoLancamento.RECEITA, categoria = CategoriaLancamento.ARRENDAMENTO,
                valorCentavos = 450_000L, data = dia(mesAtual, 5)
            ),
            Lancamento(
                id = "exemplo-8", propriedadeId = ID_SITIO,
                tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.SEMENTES,
                valorCentavos = 210_000L, data = dia(mesAtual, 15), descricao = "Semente de soja"
            ),
            Lancamento(
                id = "exemplo-9", propriedadeId = ID_SITIO,
                tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.MANUTENCAO,
                valorCentavos = 63_000L, data = dia(mesPassado, 28), descricao = "Conserto da bomba"
            )
        )
    }
}
