package br.joao.agrosaldo.feature.lancamento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.digitosParaCentavos
import br.joao.agrosaldo.core.util.formatarBr
import br.joao.agrosaldo.core.util.paraLocalDateOuNulo
import br.joao.agrosaldo.core.util.rotuloDeData
import br.joao.agrosaldo.core.util.rotuloRelativoCurto
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class LancamentoFormUiState(
    val tipo: TipoLancamento = TipoLancamento.DESPESA,
    val categoria: CategoriaLancamento = CategoriaLancamento.ENERGIA,
    /** Só dígitos. "125050" é exibido como R$ 1.250,50. */
    val valorDigitos: String = "",
    val data: String = LocalDate.now().formatarBr(),
    val propriedadeId: String = "",
    val descricao: String = "",
    val propriedades: List<Propriedade> = emptyList(),
    val erroValor: String? = null,
    val erroData: String? = null,
    val erroGeral: String? = null,
    val ehEdicao: Boolean = false,
    val salvo: Boolean = false,
    val excluido: Boolean = false,
    /** Gravando: o botão fica desligado, para dois toques não criarem dois lançamentos. */
    val salvando: Boolean = false,
    /** false até ler as propriedades (e, na edição, o lançamento) do banco. */
    val carregado: Boolean = false,
    /** Para escrever "Hoje, 4 out" no campo da data. */
    val hoje: LocalDate = LocalDate.now()
) {
    /** Só as categorias que pertencem ao tipo escolhido. */
    val categoriasDisponiveis: List<CategoriaLancamento>
        get() = CategoriaLancamento.porTipo(tipo)

    val nomePropriedadeSelecionada: String
        get() = propriedades.firstOrNull { it.id == propriedadeId }?.nome ?: "Selecione"

    val valorCentavos: Long
        get() = valorDigitos.digitosParaCentavos() ?: 0L

    /** "Hoje, 4 out", "Ontem, 3 out" ou "28 set". Data inválida aparece como foi digitada. */
    val rotuloData: String
        get() = data.paraLocalDateOuNulo()?.rotuloDeData(hoje) ?: data

    /** A linha embaixo do valor: "Combustível · Hoje · Fazenda São João". */
    val resumo: String
        get() = listOfNotNull(
            categoria.rotulo,
            data.paraLocalDateOuNulo()?.rotuloRelativoCurto(hoje),
            propriedades.firstOrNull { it.id == propriedadeId }?.nome
        ).joinToString(" · ")
}

/**
 * @param lancamentoId preenchido ao EDITAR um lançamento.
 * @param copiaDe preenchido ao LANÇAR DE NOVO: abre um lançamento novo já com
 *   tipo, categoria, valor, propriedade e descrição de outro, e a data de hoje.
 *   É o caso da conta de energia, que vem todo mês parecida.
 */
class LancamentoFormViewModel(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository,
    private val lancamentoId: String?,
    private val copiaDe: String? = null,
    private val desfazer: Desfazer = Desfazer(),
    private val hoje: () -> LocalDate = LocalDate::now,
    /** Veio dos atalhos "Despesa" e "Receita" do Início: já abre no tipo certo. */
    tipoInicial: TipoLancamento? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LancamentoFormUiState(ehEdicao = lancamentoId != null, data = hoje().formatarBr(), hoje = hoje()).let { estado ->
            if (tipoInicial == null) estado
            else estado.copy(tipo = tipoInicial, categoria = CategoriaLancamento.porTipo(tipoInicial).first())
        }
    )
    val uiState: StateFlow<LancamentoFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // first() pega o valor atual do Flow e encerra. Aqui queremos só
            // a foto do momento pra preencher o seletor, não observar mudanças.
            val propriedades = propriedadeRepository.observarTodas().first()
            // A escolhida pode ter sido excluída: aí vale a primeira da lista,
            // senão o seletor mostraria "Selecione" com um id que não existe.
            val selecionada = SelecaoAtual.propriedadeId.value
                ?.takeIf { id -> propriedades.any { it.id == id } }
                ?: propriedades.firstOrNull()?.id.orEmpty()

            val origem = (lancamentoId ?: copiaDe)?.let { lancamentoRepository.buscarPorId(it) }
            if (origem != null) {
                val ehCopia = lancamentoId == null
                _uiState.value = LancamentoFormUiState(
                    tipo = origem.tipo,
                    categoria = origem.categoria,
                    valorDigitos = origem.valorCentavos.toString(),
                    // Lançar de novo é registrar o de agora: começa com a data de hoje.
                    data = if (ehCopia) hoje().formatarBr() else origem.data.formatarBr(),
                    propriedadeId = origem.propriedadeId,
                    descricao = origem.descricao,
                    propriedades = propriedades,
                    ehEdicao = !ehCopia,
                    hoje = hoje(),
                    carregado = true
                )
                return@launch
            }

            _uiState.update {
                it.copy(propriedades = propriedades, propriedadeId = selecionada, carregado = true)
            }
        }
    }

    /**
     * Trocar o tipo obriga a trocar a categoria junto.
     *
     * Sem isso, escolher "Adubo" e depois marcar "Receita" montaria um
     * Lancamento inválido, e o require do modelo lançaria exceção no salvar.
     * Manter o estado sempre coerente é mais barato que tratar o erro depois.
     */
    fun aoMudarTipo(tipo: TipoLancamento) = _uiState.update {
        it.copy(tipo = tipo, categoria = CategoriaLancamento.porTipo(tipo).first())
    }

    fun aoMudarCategoria(categoria: CategoriaLancamento) =
        _uiState.update { it.copy(categoria = categoria) }

    fun aoMudarValor(entrada: String) = _uiState.update {
        it.copy(valorDigitos = entrada.filter { c -> c.isDigit() }.take(MAXIMO_DE_DIGITOS), erroValor = null)
    }

    /**
     * Tecla do teclado numérico da tela ("1", "00"...). Funciona como a
     * maquininha de cartão: os dígitos entram pela direita, nos centavos.
     *
     * Zero à esquerda não entra (tocar "00" com o valor vazio não faz nada),
     * e uma tecla que passaria de 9 dígitos é ignorada inteira, em vez de
     * entrar pela metade.
     */
    fun aoTeclar(digitos: String) = _uiState.update { estado ->
        // Antes de carregar a edição, o valor do banco ainda vai chegar e
        // apagaria o que fosse digitado agora.
        if (!estado.carregado || digitos.isEmpty() || !digitos.all { it.isDigit() }) return@update estado
        val novo = (estado.valorDigitos + digitos).trimStart('0')
        if (novo.length > MAXIMO_DE_DIGITOS) estado else estado.copy(valorDigitos = novo, erroValor = null)
    }

    fun aoApagarDigito() = _uiState.update {
        if (!it.carregado) it else it.copy(valorDigitos = it.valorDigitos.dropLast(1), erroValor = null)
    }

    /** Segurar o ⌫ apaga o valor inteiro. */
    fun aoLimparValor() = _uiState.update { if (!it.carregado) it else it.copy(valorDigitos = "", erroValor = null) }

    fun aoMudarData(v: String) = _uiState.update { it.copy(data = v, erroData = null) }

    /** Data escolhida no calendário. */
    fun aoEscolherData(data: LocalDate) = _uiState.update { it.copy(data = data.formatarBr(), erroData = null) }

    // Atalhos: a maioria dos lançamentos é do dia, ou do dia anterior.
    fun aoUsarHoje() = aoEscolherData(hoje())
    fun aoUsarOntem() = aoEscolherData(hoje().minusDays(1))
    fun aoMudarDescricao(v: String) = _uiState.update { it.copy(descricao = v) }
    fun aoMudarPropriedade(id: String) = _uiState.update { it.copy(propriedadeId = id) }

    fun aoSalvar() {
        val estado = _uiState.value
        // Segundo toque enquanto grava (ou depois de gravar): ignora.
        if (estado.salvando || estado.salvo) return
        val centavos = estado.valorDigitos.digitosParaCentavos()
        val data = estado.data.paraLocalDateOuNulo()

        var houveErro = false
        var novo = estado

        if (centavos == null || centavos <= 0L) {
            novo = novo.copy(erroValor = "Informe um valor maior que zero.")
            houveErro = true
        }
        if (data == null) {
            novo = novo.copy(erroData = "Data inválida. Escolha a data de novo.")
            houveErro = true
        } else if (data.isAfter(hoje().plusMonths(MESES_NO_FUTURO))) {
            // RN07 também está no modelo, mas aqui a mensagem sai no campo certo.
            novo = novo.copy(erroData = "A data pode ser no máximo 12 meses à frente.")
            houveErro = true
        }
        if (estado.propriedadeId.isBlank()) {
            novo = novo.copy(erroGeral = "Escolha a propriedade do lançamento.")
            houveErro = true
        }

        // Mesma técnica do formulário de propriedade: checar os nulos aqui
        // faz o compilador garantir que centavos e data existem daqui pra baixo.
        if (houveErro || centavos == null || data == null) {
            _uiState.value = novo
            return
        }

        _uiState.update { it.copy(salvando = true) }
        viewModelScope.launch {
            try {
                val lancamento = Lancamento(
                    id = lancamentoId ?: UUID.randomUUID().toString(),
                    propriedadeId = estado.propriedadeId,
                    tipo = estado.tipo,
                    categoria = estado.categoria,
                    valorCentavos = centavos,
                    data = data,
                    descricao = estado.descricao.trim()
                )
                lancamentoRepository.salvar(lancamento)
                // As listas passam a mostrar a propriedade e o mês do que acabou
                // de ser salvo: lançar em agosto olhando outubro não "some".
                SelecaoAtual.selecionar(lancamento.propriedadeId)
                SelecaoAtual.selecionarMes(YearMonth.from(lancamento.data))
                _uiState.update { it.copy(salvo = true, erroGeral = null) }
            } catch (e: IllegalArgumentException) {
                // Rede de segurança: se alguma regra do modelo passar batido
                // na validação da tela, a mensagem dele aparece aqui.
                _uiState.update { it.copy(erroGeral = e.message ?: "Dados inválidos.") }
            } finally {
                // Deu errado (ou o banco falhou): o botão volta a funcionar.
                _uiState.update { if (it.salvo) it else it.copy(salvando = false) }
            }
        }
    }

    /**
     * Apaga na hora e oferece "Desfazer" na barra de baixo, em vez de
     * perguntar "tem certeza?" antes. O lançamento apagado fica guardado
     * na oferta até ela sumir.
     */
    fun aoExcluir() {
        val id = lancamentoId ?: return
        viewModelScope.launch {
            val apagado = lancamentoRepository.buscarPorId(id)
            lancamentoRepository.excluir(id)
            if (apagado != null) {
                desfazer.oferecer("Lançamento excluído") { lancamentoRepository.salvar(apagado) }
            }
            _uiState.update { it.copy(excluido = true) }
        }
    }

    companion object {
        /** RN07: até 12 meses à frente (parcela de financiamento, arrendamento combinado). */
        const val MESES_NO_FUTURO = 12L

        /** R$ 9.999.999,99 — mais que isso não cabe na tela nem faz sentido num sítio. */
        const val MAXIMO_DE_DIGITOS = 9

        fun Factory(
            lancamentoId: String?,
            copiaDe: String? = null,
            tipoInicial: TipoLancamento? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LancamentoFormViewModel(
                    lancamentoRepository = Repositorios.lancamentos,
                    propriedadeRepository = Repositorios.propriedades,
                    lancamentoId = lancamentoId,
                    copiaDe = copiaDe,
                    desfazer = Repositorios.desfazer,
                    tipoInicial = tipoInicial
                )
            }
        }
    }
}
