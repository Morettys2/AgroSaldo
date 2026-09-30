package br.joao.agrosaldo.domain.model

import java.time.LocalDate
import java.util.UUID

/** Natureza do lançamento: dinheiro que entra ou dinheiro que sai. */
enum class TipoLancamento { RECEITA, DESPESA }

/**
 * Categorias fixas do domínio, exatamente as sete despesas e as receitas
 * definidas na proposta do trabalho (RF06).
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

    VENDA_SAFRA(TipoLancamento.RECEITA, "Venda de safra"),
    ARRENDAMENTO(TipoLancamento.RECEITA, "Arrendamento"),
    OUTROS(TipoLancamento.RECEITA, "Outros");

    companion object {
        /** Alimenta o seletor de categoria do formulário. */
        fun porTipo(tipo: TipoLancamento): List<CategoriaLancamento> =
            entries.filter { it.tipo == tipo }
    }
}


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


    val valorComSinal: Long
        get() = if (tipo == TipoLancamento.RECEITA) valorCentavos else -valorCentavos

    val ehReceita: Boolean get() = tipo == TipoLancamento.RECEITA
}
