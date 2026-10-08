package br.joao.agrosaldo.feature.lancamento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.DIGITOS_MAXIMOS_DO_VALOR
import br.joao.agrosaldo.core.util.digitosParaCentavos
import br.joao.agrosaldo.core.util.formatarBr
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.paraLocalDateOuNulo
import br.joao.agrosaldo.core.util.rotuloDeData
import br.joao.agrosaldo.core.util.rotuloRelativoCurto
import br.joao.agrosaldo.core.util.tentar
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
        get() = propriedades.firstOrNull { it.id == propriedadeId }?.nome ?: "Selecione"

    val valorCentavos: Long
        get() = valorDigitos.digitosParaCentavos() ?: 0L

    /** "Hoje, 4 out", "Ontem, 3 out" ou "28 set"; data inválida é exibida como digitada. */
    val rotuloData: String
        get() = data.paraLocalDateOuNulo()?.rotuloDeData(hoje) ?: data

    /** Resumo exibido sob o valor: "Combustível · Hoje · Fazenda São João". */
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
 */
class LancamentoFormViewModel(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository,
    private val lancamentoId: String?,
    private val copiaDe: String? = null,
    private val desfazer: Desfazer = Desfazer(),
    private val hoje: () -> LocalDate = LocalDate::now,
    tipoInicial: TipoLancamento? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LancamentoFormUiState(ehEdicao = lancamentoId != null, data = hoje().formatarBr(), hoje = hoje()).let { estado ->
            if (tipoInicial == null) estado
            else estado.copy(tipo = tipoInicial, categoria = CategoriaLancamento.porTipo(tipoInicial).first())
        }
    )
    val uiState: StateFlow<LancamentoFormUiState> = _uiState.asStateFlow()

    /** Mês do lançamento em edição como estava no banco; nulo na criação e na cópia. */
    private var mesOriginal: YearMonth? = null

    init {
        viewModelScope.launch {
            val propriedades = propriedadeRepository.observarTodas().first()
            // A propriedade selecionada pode ter sido excluída; cai para a primeira da lista.
            val selecionada = SelecaoAtual.propriedadeId.value
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
                )
                return@launch
            }

            _uiState.update {
                it.copy(propriedades = propriedades, propriedadeId = selecionada, carregado = true)
            }
        }
    }

    /**
     * Troca o tipo e reseta a categoria para a primeira do novo tipo, mantendo o par
     * tipo/categoria sempre válido (o `require` de [Lancamento] rejeitaria o contrário).
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
     * Tecla do teclado numérico ("1", "00"...). Os dígitos entram pela direita, nos centavos.
     * Zeros à esquerda são descartados e uma tecla que excederia [MAXIMO_DE_DIGITOS] é
     * ignorada por inteiro.
     */
    fun aoTeclar(digitos: String) = _uiState.update { estado ->
        // Antes do carregamento, o valor vindo do banco sobrescreveria o que fosse digitado.
        if (!estado.carregado || digitos.isEmpty() || !digitos.all { it.isDigit() }) return@update estado
        val novo = (estado.valorDigitos + digitos).trimStart('0')
        if (novo.length > MAXIMO_DE_DIGITOS) estado else estado.copy(valorDigitos = novo, erroValor = null)
    }

    fun aoApagarDigito() = _uiState.update {
        if (!it.carregado) it else it.copy(valorDigitos = it.valorDigitos.dropLast(1), erroValor = null)
    }

    /** Long press no ⌫: zera o valor. */
    fun aoLimparValor() = _uiState.update { if (!it.carregado) it else it.copy(valorDigitos = "", erroValor = null) }

    fun aoMudarData(v: String) = _uiState.update { it.copy(data = v, erroData = null) }

    fun aoEscolherData(data: LocalDate) = _uiState.update { it.copy(data = data.formatarBr(), erroData = null) }

    fun aoUsarHoje() = aoEscolherData(hoje())
    fun aoUsarOntem() = aoEscolherData(hoje().minusDays(1))
    fun aoMudarDescricao(v: String) = _uiState.update { it.copy(descricao = v) }
    fun aoMudarPropriedade(id: String) = _uiState.update { it.copy(propriedadeId = id) }

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
                SelecaoAtual.selecionar(lancamento.propriedadeId)
                val mes = YearMonth.from(lancamento.data)
                if (mes != mesOriginal) SelecaoAtual.selecionarMes(mes)
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
                val apagado = lancamentoRepository.buscarPorId(id)
                lancamentoRepository.excluir(id)
                apagado
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
