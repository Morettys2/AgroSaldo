package br.joao.agrosaldo.feature.lancamento

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.DIGITOS_MAXIMOS_DO_VALOR
import br.joao.agrosaldo.core.util.TextosComuns
import br.joao.agrosaldo.core.util.digitosParaCentavos
import br.joao.agrosaldo.core.util.formatarBr
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.paraLocalDateOuNulo
import br.joao.agrosaldo.core.util.rotuloDeData
import br.joao.agrosaldo.core.util.rotuloRelativoCurto
import br.joao.agrosaldo.core.util.tentar
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.RelogioDoAparelho
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.di.dependencias
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

data class LancamentoFormUiState(
    val tipo: TipoLancamento = TipoLancamento.DESPESA,
    val categoria: CategoriaLancamento = CategoriaLancamento.ENERGIA,
    /** Valor em centavos como string de dígitos: "125050" = R$ 1.250,50. */
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
    /** Gravação em andamento; desabilita o botão para evitar lançamento duplicado. */
    val salvando: Boolean = false,
    /** false até carregar as propriedades (e, na edição/cópia, o lançamento de origem). */
    val carregado: Boolean = false,
    /** O lançamento em edição não existe mais (ex.: foi excluído); a tela avisa e fecha. */
    val naoEncontrado: Boolean = false,
    /** Referência para os rótulos relativos de data ("Hoje", "Ontem"). */
    val hoje: LocalDate = LocalDate.now()
) {
    val categoriasDisponiveis: List<CategoriaLancamento>
        get() = CategoriaLancamento.porTipo(tipo)

    val nomePropriedadeSelecionada: String
        get() = propriedades.firstOrNull { it.id == propriedadeId }?.nome ?: TextosComuns.ESCOLHA_A_PROPRIEDADE

    val valorCentavos: Long
        get() = valorDigitos.digitosParaCentavos() ?: 0L

    /** "Hoje, 4 out", "Ontem, 3 out" ou "28 set"; data inválida (estado restaurado) é exibida como está. */
    val rotuloData: String
        get() = data.paraLocalDateOuNulo()?.rotuloDeData(hoje) ?: data

    /** Resumo exibido sob o valor: "Combustível · Hoje · Fazenda Santa Clara". */
    val resumo: String
        get() = listOfNotNull(
            categoria.rotulo,
            data.paraLocalDateOuNulo()?.rotuloRelativoCurto(hoje),
            propriedades.firstOrNull { it.id == propriedadeId }?.nome
        ).joinToString(" · ")
}

/**
 * Formulário de criação/edição de lançamento.
 *
 * @param lancamentoId id do lançamento em edição; nulo para criação.
 * @param copiaDe id de um lançamento a duplicar ("Lançar de novo"): copia tipo, categoria,
 *   valor, propriedade e descrição, com a data de hoje e um id novo.
 * @param tipoInicial tipo pré-selecionado (atalhos "Despesa"/"Receita" do Início).
 * @param estadoSalvo guarda os campos editáveis para sobreviverem à morte do processo; na
 *   edição, o que foi restaurado vale mais que o que está no banco.
 */
class LancamentoFormViewModel(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository,
    /** Propriedade e mês escolhidos, compartilhados com as outras telas. */
    private val selecao: SelecaoAtual,
    private val lancamentoId: String?,
    private val copiaDe: String? = null,
    private val desfazer: Desfazer = Desfazer(),
    private val relogio: Clock = RelogioDoAparelho,
    tipoInicial: TipoLancamento? = null,
    private val estadoSalvo: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private fun hoje(): LocalDate = LocalDate.now(relogio)

    private val _uiState = MutableStateFlow(
        LancamentoFormUiState(ehEdicao = lancamentoId != null, data = hoje().formatarBr(), hoje = hoje()).let { estado ->
            if (tipoInicial == null) {
                estado
            } else {
                estado.copy(tipo = tipoInicial, categoria = CategoriaLancamento.porTipo(tipoInicial).first())
            }
        }
    )
    val uiState: StateFlow<LancamentoFormUiState> = _uiState.asStateFlow()

    /** Mês do lançamento em edição como estava no banco; nulo na criação e na cópia. */
    private var mesOriginal: YearMonth? = null

    init {
        viewModelScope.launch {
            val propriedades = propriedadeRepository.observarTodas().first()
            // A propriedade selecionada pode ter sido excluída; cai para a primeira da lista.
            val selecionada = selecao.propriedadeId.value
                ?.takeIf { id -> propriedades.any { it.id == id } }
                ?: propriedades.firstOrNull()?.id.orEmpty()

            val origem = (lancamentoId ?: copiaDe)?.let { lancamentoRepository.buscarPorId(it) }
            if (lancamentoId != null && origem == null) {
                // Salvar recriaria o registro com o id antigo.
                _uiState.update { it.copy(propriedades = propriedades, carregado = true, naoEncontrado = true) }
                return@launch
            }
            if (origem != null) {
                val ehCopia = lancamentoId == null
                if (!ehCopia) mesOriginal = YearMonth.from(origem.data)
                _uiState.value = LancamentoFormUiState(
                    tipo = origem.tipo,
                    categoria = origem.categoria,
                    valorDigitos = origem.valorCentavos.toString(),
                    data = if (ehCopia) hoje().formatarBr() else origem.data.formatarBr(),
                    propriedadeId = origem.propriedadeId,
                    descricao = origem.descricao,
                    propriedades = propriedades,
                    ehEdicao = !ehCopia,
                    hoje = hoje(),
                    carregado = true
                ).comCamposRestaurados()
                return@launch
            }

            _uiState.update {
                it.copy(propriedades = propriedades, propriedadeId = selecionada, carregado = true)
                    .comCamposRestaurados()
            }
        }
    }

    /** Sobrepõe os campos guardados em [estadoSalvo] antes da morte do processo. */
    private fun LancamentoFormUiState.comCamposRestaurados(): LancamentoFormUiState {
        val tipoRestaurado = estadoSalvo.get<String>(CHAVE_TIPO)
            ?.let { nome -> TipoLancamento.entries.firstOrNull { it.name == nome } }
            ?: tipo
        val categoriaRestaurada = estadoSalvo.get<String>(CHAVE_CATEGORIA)
            ?.let { nome -> CategoriaLancamento.entries.firstOrNull { it.name == nome } }
            ?: categoria
        return copy(
            tipo = tipoRestaurado,
            // O par tipo/categoria precisa continuar válido para o `require` de [Lancamento].
            categoria = categoriaRestaurada.takeIf { it.tipo == tipoRestaurado }
                ?: CategoriaLancamento.porTipo(tipoRestaurado).first(),
            valorDigitos = estadoSalvo.get<String>(CHAVE_VALOR) ?: valorDigitos,
            data = estadoSalvo.get<String>(CHAVE_DATA) ?: data,
            // A propriedade guardada pode ter sido excluída nesse meio-tempo.
            propriedadeId = estadoSalvo.get<String>(CHAVE_PROPRIEDADE)
                ?.takeIf { id -> propriedades.any { it.id == id } }
                ?: propriedadeId,
            descricao = estadoSalvo.get<String>(CHAVE_DESCRICAO) ?: descricao
        )
    }

    /**
     * Aplica uma edição da pessoa e guarda os campos editáveis em [estadoSalvo]. Antes de
     * carregar nada é guardado, para o estado padrão não sobrescrever o que seria restaurado.
     */
    private fun editar(mudanca: (LancamentoFormUiState) -> LancamentoFormUiState) {
        val estado = _uiState.updateAndGet(mudanca)
        if (!estado.carregado) return
        estadoSalvo[CHAVE_TIPO] = estado.tipo.name
        estadoSalvo[CHAVE_CATEGORIA] = estado.categoria.name
        estadoSalvo[CHAVE_VALOR] = estado.valorDigitos
        estadoSalvo[CHAVE_DATA] = estado.data
        estadoSalvo[CHAVE_PROPRIEDADE] = estado.propriedadeId
        estadoSalvo[CHAVE_DESCRICAO] = estado.descricao
    }

    /**
     * Troca o tipo e reseta a categoria para a primeira do novo tipo, mantendo o par
     * tipo/categoria sempre válido (o `require` de [Lancamento] rejeitaria o contrário).
     */
    fun aoMudarTipo(tipo: TipoLancamento) = editar {
        it.copy(tipo = tipo, categoria = CategoriaLancamento.porTipo(tipo).first())
    }

    fun aoMudarCategoria(categoria: CategoriaLancamento) = editar { it.copy(categoria = categoria) }

    /**
     * Tecla do teclado numérico ("1", "00"...). Os dígitos entram pela direita, nos centavos.
     * Zeros à esquerda são descartados e uma tecla que excederia [MAXIMO_DE_DIGITOS] é
     * ignorada por inteiro.
     */
    fun aoTeclar(digitos: String) = editar { estado ->
        // Antes do carregamento, o valor vindo do banco sobrescreveria o que fosse digitado.
        if (!estado.carregado || digitos.isEmpty() || !digitos.all { it.isDigit() }) return@editar estado
        val novo = (estado.valorDigitos + digitos).trimStart('0')
        if (novo.length > MAXIMO_DE_DIGITOS) estado else estado.copy(valorDigitos = novo, erroValor = null)
    }

    fun aoApagarDigito() = editar {
        if (!it.carregado) it else it.copy(valorDigitos = it.valorDigitos.dropLast(1), erroValor = null)
    }

    /** Long press no ⌫: zera o valor. */
    fun aoLimparValor() = editar { if (!it.carregado) it else it.copy(valorDigitos = "", erroValor = null) }

    fun aoEscolherData(data: LocalDate) = editar { it.copy(data = data.formatarBr(), erroData = null) }

    fun aoUsarHoje() = aoEscolherData(hoje())
    fun aoUsarOntem() = aoEscolherData(hoje().minusDays(1))
    fun aoMudarDescricao(v: String) = editar { it.copy(descricao = v.take(Lancamento.DESCRICAO_MAXIMA)) }
    fun aoMudarPropriedade(id: String) = editar { it.copy(propriedadeId = id) }

    fun aoSalvar() {
        val estado = _uiState.value
        // Ignora toques repetidos durante ou após a gravação.
        if (estado.salvando || estado.salvo || estado.naoEncontrado) return
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
        } else if (!Lancamento.dataDentroDoLimite(data, hoje())) {
            novo = novo.copy(erroData = "A data pode ser no máximo 12 meses à frente.")
            houveErro = true
        }
        if (estado.propriedadeId.isBlank()) {
            novo = novo.copy(erroGeral = "Escolha a propriedade do lançamento.")
            houveErro = true
        }

        // Checagem de nulos redundante com houveErro, mas habilita smart cast abaixo.
        if (houveErro || centavos == null || data == null) {
            _uiState.value = novo
            return
        }

        _uiState.update { it.copy(salvando = true) }
        viewModelScope.launch {
            tentar {
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
                // Seleciona propriedade e mês do lançamento salvo para que ele apareça nas listas.
                // Na edição, o mês só muda se a data mudou de mês: trocar o mês fecharia a busca
                // e tiraria a pessoa do mês em que estava.
                selecao.selecionar(lancamento.propriedadeId)
                val mes = YearMonth.from(lancamento.data)
                if (mes != mesOriginal) selecao.selecionarMes(mes)
            }.onSuccess {
                _uiState.update { it.copy(salvo = true, erroGeral = null) }
            }.onFailure { erro ->
                // Falha de regra do modelo não coberta pela validação da tela, ou erro de banco.
                _uiState.update {
                    it.copy(salvando = false, erroGeral = erro.mensagemParaUsuario("Não foi possível salvar. Tente de novo."))
                }
            }
        }
    }

    /**
     * Exclui imediatamente, sem confirmação, e oferece "Desfazer" via [Desfazer], que
     * guarda o lançamento removido para regravá-lo.
     */
    fun aoExcluir() {
        val id = lancamentoId ?: return
        viewModelScope.launch {
            tentar {
                // Lê e apaga numa transação só: o Desfazer recebe exatamente o que saiu do banco.
                lancamentoRepository.excluir(id)
            }.onSuccess { apagado ->
                if (apagado != null) {
                    desfazer.oferecer("Lançamento excluído") { lancamentoRepository.salvar(apagado) }
                }
                _uiState.update { it.copy(excluido = true) }
            }.onFailure { erro ->
                _uiState.update { it.copy(erroGeral = erro.mensagemParaUsuario("Não foi possível excluir. Tente de novo.")) }
            }
        }
    }

    companion object {
        /** Aviso exibido quando [LancamentoFormUiState.naoEncontrado]. */
        const val AVISO_NAO_ENCONTRADO = "Este lançamento não existe mais."

        /** RN07: limite de data futura, definido em [Lancamento]; usado para limitar o calendário. */
        const val MESES_NO_FUTURO = Lancamento.MESES_NO_FUTURO

        /** Teto de entrada: R$ 9.999.999,99. */
        const val MAXIMO_DE_DIGITOS = DIGITOS_MAXIMOS_DO_VALOR

        // Chaves do SavedStateHandle; os valores são String para caberem no Bundle.
        internal const val CHAVE_TIPO = "tipo"
        internal const val CHAVE_CATEGORIA = "categoria"
        internal const val CHAVE_VALOR = "valor"
        internal const val CHAVE_DATA = "data"
        internal const val CHAVE_PROPRIEDADE = "propriedade"
        internal const val CHAVE_DESCRICAO = "descricao"

        fun Factory(lancamentoId: String?, copiaDe: String? = null, tipoInicial: TipoLancamento? = null): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    LancamentoFormViewModel(
                        selecao = dependencias.selecao,
                        lancamentoRepository = dependencias.lancamentos,
                        propriedadeRepository = dependencias.propriedades,
                        lancamentoId = lancamentoId,
                        copiaDe = copiaDe,
                        desfazer = dependencias.desfazer,
                        tipoInicial = tipoInicial,
                        estadoSalvo = createSavedStateHandle()
                    )
                }
            }
    }
}
