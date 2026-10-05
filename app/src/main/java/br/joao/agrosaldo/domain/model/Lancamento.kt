package br.joao.agrosaldo.domain.model

import java.time.LocalDate
import java.util.UUID

/** Natureza do lançamento: dinheiro que entra ou dinheiro que sai. */
enum class TipoLancamento { RECEITA, DESPESA }

/**
 * Categorias fixas do domínio: as sete despesas e as receitas definidas na
 * proposta do trabalho (RF06), mais "Outras" de cada lado — sem elas, um
 * gasto como imposto ou frete não teria onde ser lançado.
 *
 * Cada categoria carrega o próprio [tipo]. Isso torna impossível cadastrar
 * "Venda de safra" como despesa — o init do [Lancamento] barra.
 *
 * O rótulo fica aqui, e não em strings.xml, de propósito: ele é vocabulário
 * do domínio, não texto de interface. Pôr um id de recurso aqui obrigaria o
 * modelo a importar androidx.annotation, quebrando a regra de que a camada
 * de domínio não conhece Android.
 */
enum class CategoriaLancamento(val tipo: TipoLancamento, val rotulo: String) {
    ENERGIA(TipoLancamento.DESPESA, "Energia elétrica"),
    DEFENSIVOS(TipoLancamento.DESPESA, "Defensivos"),
    SEMENTES(TipoLancamento.DESPESA, "Sementes"),
    ADUBO(TipoLancamento.DESPESA, "Adubo"),
    COMBUSTIVEL(TipoLancamento.DESPESA, "Combustível"),
    MAO_DE_OBRA(TipoLancamento.DESPESA, "Mão de obra"),
    MANUTENCAO(TipoLancamento.DESPESA, "Manutenção"),
    // Entrou depois da proposta. Fica no fim da lista de despesas de propósito:
    // é a última opção, para quando nenhuma outra serve.
    OUTRAS_DESPESAS(TipoLancamento.DESPESA, "Outras despesas"),

    VENDA_SAFRA(TipoLancamento.RECEITA, "Venda de safra"),
    ARRENDAMENTO(TipoLancamento.RECEITA, "Arrendamento"),
    // O nome OUTROS fica como está: é ele que está gravado no banco.
    OUTROS(TipoLancamento.RECEITA, "Outras receitas");

    companion object {
        /** Alimenta o seletor de categoria do formulário. */
        fun porTipo(tipo: TipoLancamento): List<CategoriaLancamento> =
            entries.filter { it.tipo == tipo }
    }
}

/**
 * MODEL — lançamento financeiro de uma propriedade.
 *
 * RN01 — todo lançamento pertence a exatamente uma propriedade.
 * RN06 — o valor é sempre positivo; quem define o sinal é o [tipo].
 *
 * As validações ficam no init: um objeto inválido simplesmente não chega a
 * existir. A tela valida antes só pra mostrar mensagem amigável — mas mesmo
 * que ela falhe, o modelo se defende.
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
        require(!data.isAfter(LocalDate.now().plusMonths(12))) {
            "Não é permitido lançamento com data superior a 12 meses (RN07)."
        }
    }

    /** Valor com sinal, usado no cálculo de saldo do período (RN03). */
    val valorComSinal: Long
        get() = if (tipo == TipoLancamento.RECEITA) valorCentavos else -valorCentavos

    val ehReceita: Boolean get() = tipo == TipoLancamento.RECEITA
}
