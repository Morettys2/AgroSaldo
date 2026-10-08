package br.joao.agrosaldo.feature.propriedade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.numeroBr
import br.joao.agrosaldo.core.util.tentar
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.model.Propriedade
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
    /** Erro que não é de um campo (o banco falhou, a sessão caiu...). */
    val erroGeral: String? = null,
    val ehEdicao: Boolean = false,
    /** Gravando: o botão fica desligado, para dois toques não criarem duas propriedades. */
    val salvando: Boolean = false,
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
    private val propriedadeId: String?,
    private val desfazer: Desfazer = Desfazer()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PropriedadeFormUiState(ehEdicao = propriedadeId != null))
    val uiState: StateFlow<PropriedadeFormUiState> = _uiState.asStateFlow()

    init {
        if (propriedadeId != null) carregar(propriedadeId)
    }

    private fun carregar(id: String) {
        viewModelScope.launch {
            propriedadeRepository.buscarPorId(id)?.let { propriedade ->
                _uiState.update {
                    it.copy(
                        nome = propriedade.nome,
                        cultura = propriedade.cultura,
                        area = propriedade.areaHectares.paraCampo(),
                        latitude = propriedade.latitude.paraCampo(),
                        longitude = propriedade.longitude.paraCampo()
                    )
                }
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

    /** Número do banco para o campo: "18", "12,5", "-21,7333" — nunca "1.0E7". */
    private fun Double.paraCampo(): String = numeroBr(agrupar = false)

    /** "12,5" e "12.5" viram o mesmo número. "NaN" e "Infinity" não contam como número. */
    private fun String.paraNumero(): Double? =
        replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    fun aoSalvar() {
        val estado = _uiState.value
        // Segundo toque enquanto grava (ou depois de gravar): ignora.
        if (estado.salvando || estado.salvo) return

        val area = estado.area.paraNumero()
        val lat = estado.latitude.paraNumero()
        val lon = estado.longitude.paraNumero()

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

        _uiState.update { it.copy(salvando = true, erroGeral = null) }
        viewModelScope.launch {
            tentar {
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
            }.onSuccess {
                _uiState.update { it.copy(salvo = true) }
            }.onFailure { erro ->
                // O botão volta a funcionar e a pessoa vê o que houve.
                _uiState.update {
                    it.copy(salvando = false, erroGeral = erro.mensagemParaUsuario("Não foi possível salvar. Tente de novo."))
                }
            }
        }
    }

    /**
     * RN02 — excluir propriedade leva junto os lançamentos dela.
     *
     * A tela confirma antes (é muita coisa de uma vez) e, mesmo assim, a barra
     * de baixo oferece "Desfazer": a propriedade e todos os lançamentos dela
     * voltam, com os mesmos ids.
     */
    fun aoExcluir() {
        val id = propriedadeId ?: return
        viewModelScope.launch {
            // O repositório apaga tudo numa transação só e devolve o que
            // apagou: é com isso que o "Desfazer" traz tudo de volta.
            tentar { propriedadeRepository.excluirComLancamentos(id) }
                .onSuccess { excluida ->
                    SelecaoAtual.limparSeFor(id)
                    if (excluida != null) {
                        desfazer.oferecer("Propriedade excluída") { propriedadeRepository.restaurar(excluida) }
                    }
                    _uiState.update { it.copy(excluido = true) }
                }
                .onFailure { erro ->
                    _uiState.update {
                        it.copy(erroGeral = erro.mensagemParaUsuario("Não foi possível excluir. Tente de novo."))
                    }
                }
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
                    propriedadeId = propriedadeId,
                    desfazer = Repositorios.desfazer
                )
            }
        }
    }
}
