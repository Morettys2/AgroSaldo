package br.joao.agrosaldo.feature.lancamento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.digitosParaCentavos
import br.joao.agrosaldo.core.util.formatarBr
import br.joao.agrosaldo.core.util.paraLocalDateOuNulo
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
    val excluido: Boolean = false
) {
    /** Só as categorias que pertencem ao tipo escolhido. */
    val categoriasDisponiveis: List<CategoriaLancamento>
        get() = CategoriaLancamento.porTipo(tipo)

    val nomePropriedadeSelecionada: String
        get() = propriedades.firstOrNull { it.id == propriedadeId }?.nome ?: "Selecione"

    val valorCentavos: Long
        get() = valorDigitos.digitosParaCentavos() ?: 0L
}

class LancamentoFormViewModel(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository,
    private val lancamentoId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(LancamentoFormUiState(ehEdicao = lancamentoId != null))
    val uiState: StateFlow<LancamentoFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // first() pega o valor atual do Flow e encerra. Aqui queremos só
            // a foto do momento pra preencher o seletor, não observar mudanças.
            val propriedades = propriedadeRepository.observarTodas().first()
            val selecionada = SelecaoAtual.propriedadeId.value
                ?: propriedades.firstOrNull()?.id.orEmpty()

            if (lancamentoId != null) {
                val existente = lancamentoRepository.buscarPorId(lancamentoId)
                if (existente != null) {
                    _uiState.value = LancamentoFormUiState(
                        tipo = existente.tipo,
                        categoria = existente.categoria,
                        valorDigitos = existente.valorCentavos.toString(),
                        data = existente.data.formatarBr(),
                        propriedadeId = existente.propriedadeId,
                        descricao = existente.descricao,
                        propriedades = propriedades,
                        ehEdicao = true
                    )
                    return@launch
                }
            }

            _uiState.update {
                it.copy(propriedades = propriedades, propriedadeId = selecionada)
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
        it.copy(valorDigitos = entrada.filter { c -> c.isDigit() }.take(9), erroValor = null)
    }

    fun aoMudarData(v: String) = _uiState.update { it.copy(data = v, erroData = null) }
    fun aoMudarDescricao(v: String) = _uiState.update { it.copy(descricao = v) }
    fun aoMudarPropriedade(id: String) = _uiState.update { it.copy(propriedadeId = id) }

    fun aoSalvar() {
        val estado = _uiState.value
        val centavos = estado.valorDigitos.digitosParaCentavos()
        val data = estado.data.paraLocalDateOuNulo()

        var houveErro = false
        var novo = estado

        if (centavos == null || centavos <= 0L) {
            novo = novo.copy(erroValor = "Informe um valor maior que zero.")
            houveErro = true
        }
        if (data == null) {
            novo = novo.copy(erroData = "Data inválida. Use o formato dd/MM/aaaa.")
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
                SelecaoAtual.selecionar(lancamento.propriedadeId)
                _uiState.update { it.copy(salvo = true, erroGeral = null) }
            } catch (e: IllegalArgumentException) {
                // Rede de segurança: se alguma regra do modelo passar batido
                // na validação da tela, a mensagem dele aparece aqui.
                _uiState.update { it.copy(erroGeral = e.message ?: "Dados inválidos.") }
            }
        }
    }

    fun aoExcluir() {
        val id = lancamentoId ?: return
        viewModelScope.launch {
            lancamentoRepository.excluir(id)
            _uiState.update { it.copy(excluido = true) }
        }
    }

    companion object {
        fun Factory(lancamentoId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LancamentoFormViewModel(
                    lancamentoRepository = Repositorios.lancamentos,
                    propriedadeRepository = Repositorios.propriedades,
                    lancamentoId = lancamentoId
                )
            }
        }
    }
}
