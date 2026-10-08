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
 * Estado do formulário de propriedade. Campos numéricos (área em hectares, latitude,
 * longitude) ficam como texto digitado e só são convertidos na validação em [PropriedadeFormViewModel.aoSalvar].
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
    /** Erro não associado a um campo (falha de banco, sessão etc.). */
    val erroGeral: String? = null,
    val ehEdicao: Boolean = false,
    /** Gravação em andamento; desabilita o botão para evitar registro duplicado. */
    val salvando: Boolean = false,
    val salvo: Boolean = false,
    val excluido: Boolean = false
)

/**
 * Formulário de criação/edição de propriedade. Toda a validação fica aqui; a tela só
 * exibe os erros do estado.
 *
 * @param propriedadeId id em edição; nulo para criação.
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

    // Editar um campo limpa o erro correspondente.
    fun aoMudarNome(v: String) = _uiState.update { it.copy(nome = v, erroNome = null) }
    fun aoMudarCultura(v: String) = _uiState.update { it.copy(cultura = v) }
    fun aoMudarArea(v: String) = _uiState.update { it.copy(area = v, erroArea = null) }
    fun aoMudarLatitude(v: String) = _uiState.update { it.copy(latitude = v, erroCoordenadas = null) }
    fun aoMudarLongitude(v: String) = _uiState.update { it.copy(longitude = v, erroCoordenadas = null) }

    /** Preenche as coordenadas obtidas do GPS com 6 casas decimais (~10 cm) e vírgula decimal. */
    fun aoUsarLocalizacao(latitude: Double, longitude: Double) = _uiState.update {
        it.copy(
            latitude = formatarCoordenada(latitude),
            longitude = formatarCoordenada(longitude),
            erroCoordenadas = null
        )
    }

    private fun formatarCoordenada(valor: Double): String =
        String.format(Locale.ROOT, "%.6f", valor).replace('.', ',')

    /** Formata para o campo: "18", "12,5", "-21,7333" (sem notação científica nem agrupamento). */
    private fun Double.paraCampo(): String = numeroBr(agrupar = false)

    /** Aceita vírgula ou ponto decimal; rejeita NaN e infinito. */
    private fun String.paraNumero(): Double? =
        replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    fun aoSalvar() {
        val estado = _uiState.value
        // Ignora toques repetidos durante ou após a gravação.
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

        // Checagem de nulos redundante com houveErro, mas habilita smart cast abaixo.
        if (houveErro || area == null || lat == null || lon == null) {
            _uiState.value = novo
            return
        }

        _uiState.update { it.copy(salvando = true, erroGeral = null) }
        viewModelScope.launch {
            tentar {
                val propriedade = Propriedade(
                    // Mesmo id na edição: o repositório faz upsert.
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
                _uiState.update {
                    it.copy(salvando = false, erroGeral = erro.mensagemParaUsuario("Não foi possível salvar. Tente de novo."))
                }
            }
        }
    }

    /**
     * RN02: exclui a propriedade e seus lançamentos (a tela pede confirmação antes).
     * Oferece "Desfazer", que restaura tudo com os mesmos ids.
     */
    fun aoExcluir() {
        val id = propriedadeId ?: return
        viewModelScope.launch {
            // Exclusão transacional; retorna os dados removidos para o "Desfazer".
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
        /** @param propriedadeId id vindo da rota de navegação; nulo para criação. */
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
