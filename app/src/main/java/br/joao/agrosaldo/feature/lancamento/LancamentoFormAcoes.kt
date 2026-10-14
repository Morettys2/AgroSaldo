package br.joao.agrosaldo.feature.lancamento

import androidx.compose.runtime.Immutable
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import java.time.LocalDate

/**
 * Tudo o que a tela de lançamento pode pedir, num objeto só.
 *
 * Antes o `LancamentoFormScreen` recebia 15 lambdas soltas: chamar a tela (num @Preview ou
 * num teste) era uma parede de `aoX = {}`, e trocar uma ação de lugar quebrava todas as
 * chamadas. Com a classe, cada ação tem padrão "não faz nada", e o preview ou o teste
 * preenche só as que interessam: `LancamentoFormAcoes(aoSalvar = { salvou = true })`.
 *
 * É imutável (só `val` e lambdas), então o Compose a trata como estável e não recompõe a
 * tela à toa quando o mesmo objeto chega de novo.
 */
@Immutable
data class LancamentoFormAcoes(
    val aoFechar: () -> Unit = {},
    val aoMudarTipo: (TipoLancamento) -> Unit = {},
    val aoMudarCategoria: (CategoriaLancamento) -> Unit = {},
    /** Dígito ("0"…"9") ou "00", como no teclado da tela. */
    val aoTeclar: (String) -> Unit = {},
    val aoApagarDigito: () -> Unit = {},
    val aoLimparValor: () -> Unit = {},
    val aoEscolherData: (LocalDate) -> Unit = {},
    val aoUsarHoje: () -> Unit = {},
    val aoUsarOntem: () -> Unit = {},
    val aoMudarPropriedade: (String) -> Unit = {},
    val aoMudarDescricao: (String) -> Unit = {},
    val aoSalvar: () -> Unit = {},
    val aoExcluir: () -> Unit = {},
    val aoLancarDeNovo: () -> Unit = {},
    val aoCadastrarPropriedade: () -> Unit = {}
)
