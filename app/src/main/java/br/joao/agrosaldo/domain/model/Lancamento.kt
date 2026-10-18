package br.joao.agrosaldo.domain.model

import java.time.LocalDate
import java.util.UUID

enum class TipoLancamento { RECEITA, DESPESA }

/**
 * Categorias fixas (RF06), mais uma categoria "Outras" para cada tipo.
 *
 * Cada categoria pertence a um único [tipo]; [Lancamento] rejeita combinações
 * inconsistentes. O rótulo fica no domínio (e não em strings.xml) para manter
 * esta camada livre de dependências Android.
 *
 * O nome da constante é persistido no banco: não renomear sem migração.
 */
enum class CategoriaLancamento(val tipo: TipoLancamento, val rotulo: String) {
    ENERGIA(TipoLancamento.DESPESA, "Energia elétrica"),
    DEFENSIVOS(TipoLancamento.DESPESA, "Defensivos"),
    SEMENTES(TipoLancamento.DESPESA, "Sementes"),
    ADUBO(TipoLancamento.DESPESA, "Adubo"),
    COMBUSTIVEL(TipoLancamento.DESPESA, "Combustível"),
    MAO_DE_OBRA(TipoLancamento.DESPESA, "Mão de obra"),
    MANUTENCAO(TipoLancamento.DESPESA, "Manutenção"),

    // Mantida por último entre as despesas: a ordem define a do seletor.
    OUTRAS_DESPESAS(TipoLancamento.DESPESA, "Outras despesas"),

    VENDA_SAFRA(TipoLancamento.RECEITA, "Venda de safra"),
    ARRENDAMENTO(TipoLancamento.RECEITA, "Arrendamento"),

    // Nome persistido no banco; o rótulo pode mudar, a constante não.
    OUTROS(TipoLancamento.RECEITA, "Outras receitas");

    companion object {
        /** Categorias do [tipo], na ordem de declaração. */
        fun porTipo(tipo: TipoLancamento): List<CategoriaLancamento> = entries.filter { it.tipo == tipo }
    }
}

/**
 * Lançamento financeiro de uma propriedade. Valores em centavos.
 *
 * - RN01: pertence a exatamente uma propriedade.
 * - RN06: [valorCentavos] é sempre positivo; o sinal vem do [tipo].
 *
 * O init só valida invariantes independentes do tempo. O limite de data futura
 * (RN07) depende do relógio e é checado ao salvar via [dataDentroDoLimite];
 * no init, registros já gravados poderiam falhar ao serem lidos.
 */
data class Lancamento(
    val id: String = UUID.randomUUID().toString(),
    val propriedadeId: String,
    val tipo: TipoLancamento,
    val categoria: CategoriaLancamento,
    val valorCentavos: Long,
    val data: LocalDate,
    val descricao: String = ""
) {
    init {
        require(propriedadeId.isNotBlank()) {
            "Todo lançamento precisa de uma propriedade (RN01)."
        }
        require(valorCentavos > 0L) {
            "O valor do lançamento deve ser positivo (RN06)."
        }
        require(categoria.tipo == tipo) {
            "A categoria ${categoria.rotulo} não pertence ao tipo $tipo."
        }
        require(descricao.length <= DESCRICAO_MAXIMA) {
            "A descrição pode ter no máximo $DESCRICAO_MAXIMA caracteres."
        }
    }

    /** Valor com sinal, usado no cálculo de saldo do período (RN03). */
    val valorComSinal: Long
        get() = if (tipo == TipoLancamento.RECEITA) valorCentavos else -valorCentavos

    val ehReceita: Boolean get() = tipo == TipoLancamento.RECEITA

    companion object {
        /** Tamanho máximo da descrição: texto sem limite travaria a lista e o banco. */
        const val DESCRICAO_MAXIMA = 200

        /** RN07: limite de meses no futuro para a data de um lançamento. */
        const val MESES_NO_FUTURO = 12L

        /** RN07: true se [data] não passa de [MESES_NO_FUTURO] meses após [hoje]. */
        fun dataDentroDoLimite(data: LocalDate, hoje: LocalDate): Boolean = !data.isAfter(hoje.plusMonths(MESES_NO_FUTURO))
    }
}
