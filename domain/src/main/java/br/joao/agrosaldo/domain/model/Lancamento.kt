package br.joao.agrosaldo.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Tipo do lançamento: RECEITA (entrada) ou DESPESA (saída). */
enum class TipoLancamento { RECEITA, DESPESA }

/**
 * Categorias fixas de lançamento; cada uma pertence a um único [tipo] e [Lancamento] recusa
 * combinações inconsistentes. O rótulo fica no domínio (e não em strings.xml) para esta camada
 * não depender do Android. O nome da constante é gravado no banco: não renomear sem migração.
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
 * Modelo de domínio de um lançamento financeiro, sempre ligado a uma propriedade. O valor é `Long`
 * em centavos e sempre positivo; o sinal vem do [tipo].
 *
 * O `init` valida só o que não depende do relógio. O limite de data futura é checado ao salvar
 * ([dataDentroDoLimite]): no `init`, um registro já gravado poderia falhar ao ser lido.
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
            "Todo lançamento precisa de uma propriedade."
        }
        require(valorCentavos > 0L) {
            "O valor do lançamento deve ser positivo."
        }
        require(categoria.tipo == tipo) {
            "A categoria ${categoria.rotulo} não pertence ao tipo $tipo."
        }
        require(descricao.length <= DESCRICAO_MAXIMA) {
            "A descrição pode ter no máximo $DESCRICAO_MAXIMA caracteres."
        }
    }

    /** Valor com sinal: positivo para receita e negativo para despesa (usado no saldo). */
    val valorComSinal: Long
        get() = if (tipo == TipoLancamento.RECEITA) valorCentavos else -valorCentavos

    val ehReceita: Boolean get() = tipo == TipoLancamento.RECEITA

    companion object {
        /** Tamanho máximo da descrição, validado no `init`. */
        const val DESCRICAO_MAXIMA = 200

        /** Limite de meses no futuro para a data de um lançamento. */
        const val MESES_NO_FUTURO = 12L

        /** true se [data] não passa de [MESES_NO_FUTURO] meses depois de [hoje]. */
        fun dataDentroDoLimite(data: LocalDate, hoje: LocalDate): Boolean = !data.isAfter(hoje.plusMonths(MESES_NO_FUTURO))
    }
}

/**
 * Lançamento apagado e o instante da última gravação dele ([gravadoEm]). O "Desfazer" regrava com o
 * mesmo instante, e o lançamento volta ao mesmo lugar da lista (a ordem do dia segue a última gravação).
 */
data class LancamentoExcluido(val lancamento: Lancamento, val gravadoEm: Instant)
