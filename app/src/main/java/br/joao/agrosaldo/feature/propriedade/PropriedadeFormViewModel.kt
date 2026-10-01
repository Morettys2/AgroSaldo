package br.joao.agrosaldo.feature.propriedade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Estado do formulário.
 *
 * Os campos são String, e não Double, porque é isso que o usuário digita.
 * "12,5" e "12.5" e "abc" são todos textos válidos de se ter num campo —
 * a conversão para número acontece só na validação, no momento de salvar.
 *
 * Guardar Double aqui obrigaria a converter a cada tecla digitada, e o campo
 * apagaria o que a pessoa escreveu no meio da digitação.
 */
data class PropriedadeFormUiState(
    val nome: String = "",
    val cultura: String = "",
    val area: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val erroNome: String? = null,
    val erroArea: String? = null,
    val erroCoordenadas: String? = null,
    val ehEdicao: Boolean = false,
    val salvo: Boolean = false,
    val excluido: Boolean = false
)

/**
 * ViewModel do formulário — o fluxo do MVVM nos dois sentidos.
 *
 * Na lista, o dado só descia: repositório → ViewModel → tela.
 * Aqui ele também sobe: a tela avisa cada tecla digitada, o ViewModel valida
 * e devolve um estado novo, com erro ou sem.
 *
 * A tela nunca decide se um dado é válido. Ela só mostra o que o ViewModel
 * disser. É isso que permite testar toda a regra de validação sem abrir o app.
 */
class PropriedadeFormViewModel(
    private val propriedadeRepository: PropriedadeRepository,
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(PropriedadeFormUiState(ehEdicao = propriedadeId != null))
    val uiState: StateFlow<PropriedadeFormUiState> = _uiState.asStateFlow()

    init {
        if (propriedadeId != null) carregar(propriedadeId)
    }

    private fun carregar(id: String) {
        viewModelScope.launch {
            propriedadeRepository.buscarPorId(id)?.let { propriedade ->
                _uiState.value = PropriedadeFormUiState(
                    nome = propriedade.nome,
                    cultura = propriedade.cultura,
                    // O usuário brasileiro digita vírgula, então mostramos vírgula.
                    area = propriedade.areaHectares.toString().replace('.', ','),
                    latitude = propriedade.latitude.toString().replace('.', ','),
                    longitude = propriedade.longitude.toString().replace('.', ','),
                    ehEdicao = true
                )
            }
        }
    }

    // Cada mudança limpa o erro daquele campo. Deixar o erro na tela enquanto
    // a pessoa corrige é o tipo de detalhe que faz um formulário parecer hostil.
    fun aoMudarNome(v: String) = _uiState.update { it.copy(nome = v, erroNome = null) }
    fun aoMudarCultura(v: String) = _uiState.update { it.copy(cultura = v) }
    fun aoMudarArea(v: String) = _uiState.update { it.copy(area = v, erroArea = null) }
    fun aoMudarLatitude(v: String) = _uiState.update { it.copy(latitude = v, erroCoordenadas = null) }
    fun aoMudarLongitude(v: String) = _uiState.update { it.copy(longitude = v, erroCoordenadas = null) }

    /**
     * O GPS do celular encontrou onde a pessoa está. Preenche os dois campos
     * com 6 casas (≈ 10 cm de precisão — mais que suficiente) e com vírgula,
     * que é como o produtor leria e editaria.
     */
    fun aoUsarLocalizacao(latitude: Double, longitude: Double) = _uiState.update {
        it.copy(
            latitude = formatarCoordenada(latitude),
            longitude = formatarCoordenada(longitude),
            erroCoordenadas = null
        )
    }

    private fun formatarCoordenada(valor: Double): String =
        String.format(Locale.ROOT, "%.6f", valor).replace('.', ',')

    fun aoSalvar() {
        val estado = _uiState.value

        // Aceita vírgula ou ponto: "12,5" e "12.5" viram o mesmo número.
        val area = estado.area.replace(',', '.').toDoubleOrNull()
        val lat = estado.latitude.replace(',', '.').toDoubleOrNull()
        val lon = estado.longitude.replace(',', '.').toDoubleOrNull()

        var houveErro = false
        var novo = estado

        if (estado.nome.isBlank()) {
            novo = novo.copy(erroNome = "Informe o nome da propriedade.")
            houveErro = true
        }
        if (area == null || area <= 0.0) {
            novo = novo.copy(erroArea = "A área deve ser um número maior que zero.")
            houveErro = true
        }
        if (lat == null || lon == null) {
            novo = novo.copy(erroCoordenadas = "Latitude e longitude precisam ser números.")
            houveErro = true
        } else if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            novo = novo.copy(erroCoordenadas = "Coordenadas fora do intervalo válido.")
            houveErro = true
        }

        // Se algum deles for nulo aqui, um erro já foi marcado acima. Checar
        // de novo nesta linha faz o compilador PROVAR que, daqui pra baixo,
        // os três existem — sem "!!", que derrubaria o app se errássemos.
        if (houveErro || area == null || lat == null || lon == null) {
            _uiState.value = novo
            return
        }

        viewModelScope.launch {
            val propriedade = Propriedade(
                // Na edição mantém o mesmo id: é isso que faz o repositório
                // atualizar o registro em vez de criar uma cópia.
                id = propriedadeId ?: UUID.randomUUID().toString(),
                nome = estado.nome.trim(),
                cultura = estado.cultura.trim().ifBlank { "Não informada" },
                areaHectares = area,
                latitude = lat,
                longitude = lon
            )
            propriedadeRepository.salvar(propriedade)
            SelecaoAtual.selecionar(propriedade.id)
            _uiState.update { it.copy(salvo = true) }
        }
    }

    /**
     * RN02 — excluir propriedade leva junto os lançamentos dela.
     *
     * A decisão é explícita e não silenciosa: a tela confirma com o usuário
     * antes de chamar esta função.
     */
    fun aoExcluir() {
        val id = propriedadeId ?: return
        viewModelScope.launch {
            lancamentoRepository.excluirDaPropriedade(id)
            propriedadeRepository.excluir(id)
            SelecaoAtual.limparSeFor(id)
            _uiState.update { it.copy(excluido = true) }
        }
    }

    companion object {
        /**
         * A fábrica recebe o id da rota. Assim o ViewModel não precisa conhecer
         * SavedStateHandle nem nada de navegação — ele só recebe um String?.
         */
        fun Factory(propriedadeId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PropriedadeFormViewModel(
                    propriedadeRepository = Repositorios.propriedades,
                    lancamentoRepository = Repositorios.lancamentos,
                    propriedadeId = propriedadeId
                )
            }
        }
    }
}
