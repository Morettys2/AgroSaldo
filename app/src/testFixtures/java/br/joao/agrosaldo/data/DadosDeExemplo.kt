package br.joao.agrosaldo.data

import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import java.time.LocalDate
import java.time.YearMonth

/**
 * Massa de dados fixa dos testes (unitários e instrumentados). Fica em `src/testFixtures`,
 * fora do APK: o app sempre começa com o banco vazio, inclusive no build debug.
 * Nomes e coordenadas são fictícios.
 */
object DadosDeExemplo {

    const val ID_FAZENDA = "exemplo-fazenda"
    const val ID_SITIO = "exemplo-sitio-boa-vista"

    fun propriedades(): List<Propriedade> = listOf(
        Propriedade(
            id = ID_FAZENDA,
            nome = "Fazenda Santa Clara",
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

    /** Lançamentos no mês de [hoje] e no anterior (exercita a navegação entre meses, RF10). */
    fun lancamentos(hoje: LocalDate = LocalDate.now()): List<Lancamento> {
        val mesAtual = YearMonth.from(hoje)
        val mesPassado = mesAtual.minusMonths(1)

        // Limita o dia a [hoje] no mês corrente (sem datas futuras) e ao fim do mês nos demais.
        fun dia(mes: YearMonth, diaDoMes: Int): LocalDate {
            val limite = if (mes == mesAtual) hoje.dayOfMonth else mes.lengthOfMonth()
            return mes.atDay(minOf(diaDoMes, limite))
        }

        return listOf(
            Lancamento(
                id = "exemplo-1",
                propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.RECEITA,
                categoria = CategoriaLancamento.VENDA_SAFRA,
                valorCentavos = 1_800_000L,
                data = dia(mesAtual, 20),
                descricao = "Venda de milho"
            ),
            Lancamento(
                id = "exemplo-2",
                propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.DESPESA,
                categoria = CategoriaLancamento.ENERGIA,
                valorCentavos = 125_000L,
                data = dia(mesAtual, 26),
                descricao = "Conta do pivô"
            ),
            Lancamento(
                id = "exemplo-3",
                propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.DESPESA,
                categoria = CategoriaLancamento.ADUBO,
                valorCentavos = 78_000L,
                data = dia(mesAtual, 12)
            ),
            Lancamento(
                id = "exemplo-4",
                propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.DESPESA,
                categoria = CategoriaLancamento.COMBUSTIVEL,
                valorCentavos = 32_000L,
                data = dia(mesAtual, 28),
                descricao = "Abastecimento do trator"
            ),
            Lancamento(
                id = "exemplo-5",
                propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.DESPESA,
                categoria = CategoriaLancamento.MAO_DE_OBRA,
                valorCentavos = 210_000L,
                data = dia(mesPassado, 22),
                descricao = "Diarista da colheita"
            ),
            Lancamento(
                id = "exemplo-6",
                propriedadeId = ID_FAZENDA,
                tipo = TipoLancamento.RECEITA,
                categoria = CategoriaLancamento.VENDA_SAFRA,
                valorCentavos = 940_000L,
                data = dia(mesPassado, 14)
            ),
            Lancamento(
                id = "exemplo-7",
                propriedadeId = ID_SITIO,
                tipo = TipoLancamento.RECEITA,
                categoria = CategoriaLancamento.ARRENDAMENTO,
                valorCentavos = 450_000L,
                data = dia(mesAtual, 5)
            ),
            Lancamento(
                id = "exemplo-8",
                propriedadeId = ID_SITIO,
                tipo = TipoLancamento.DESPESA,
                categoria = CategoriaLancamento.SEMENTES,
                valorCentavos = 210_000L,
                data = dia(mesAtual, 15),
                descricao = "Semente de soja"
            ),
            Lancamento(
                id = "exemplo-9",
                propriedadeId = ID_SITIO,
                tipo = TipoLancamento.DESPESA,
                categoria = CategoriaLancamento.MANUTENCAO,
                valorCentavos = 63_000L,
                data = dia(mesPassado, 28),
                descricao = "Conserto da bomba"
            )
        )
    }
}
