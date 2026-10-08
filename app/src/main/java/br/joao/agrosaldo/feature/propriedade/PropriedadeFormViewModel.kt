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
    /** Coordenadas fora do Brasil (provável sinal de menos esquecido); salvar de novo confirma. */
    val avisoCoordenadas: String? = null,
    /** Erro não associado a um campo (falha de banco, sessão etc.). */
    val erroGeral: String? = null,
    val ehEdicao: Boolean = false,
    /** Gravação em andamento; desabilita o botão para evitar registro duplicado. */
    val salvando: Boolean = false,
    val salvo: Boolean = false,
    val excluido: Boolean = false
) {
    /** Como a área digitada será gravada ("= 1.000 ha"); nulo se vazia ou não reconhecida. */
    val leituraDaArea: String?
        get() = (lerArea(area) as? Area.Valida)?.let { "= ${it.hectares.numeroBr(agrupar = true)} ha" }
}

/** Leitura da área digitada, no formato brasileiro. */
private sealed interface Area {
    data class Valida(val hectares: Double) : Area

    /** Só dígitos, ponto e vírgula, mas numa combinação que não dá para ler sem chutar ("1,000.5"). */
    data object Ambigua : Area

    data object Invalida : Area
}

/** "1.000" e "1.250,5": ponto no milhar, vírgula nos decimais. O primeiro grupo não começa com 0. */
private val AREA_COM_MILHAR = Regex("""^[1-9]\d{0,2}(\.\d{3})+(,\d+)?$""")

/** "1250" e "12,5". */
private val AREA_COM_VIRGULA = Regex("""^\d+(,\d+)?$""")

/** "12.5": ponto decimal, aceito só quando não é seguido de exatamente 3 dígitos (que indicariam milhar). */
private val AREA_COM_PONTO_DECIMAL = Regex("""^\d+\.(\d{1,2}|\d{4,})$""")

private val SO_DIGITOS_E_SEPARADORES = Regex("""^[\d.,]*\d[\d.,]*$""")

/**
 * Lê a área como a pessoa escreve no Brasil: "1.000" é mil, não um. O ponto decimal ("12.5")
 * continua aceito quando não pode ser confundido com milhar; o que sobra é ambíguo.
 */
private fun lerArea(texto: String): Area {
    val limpo = texto.trim()
    val numero = when {
        AREA_COM_MILHAR.matches(limpo) -> limpo.replace(".", "").replace(',', '.')
        AREA_COM_VIRGULA.matches(limpo) -> limpo.replace(',', '.')
        AREA_COM_PONTO_DECIMAL.matches(limpo) -> limpo
        SO_DIGITOS_E_SEPARADORES.matches(limpo) -> return Area.Ambigua
        else -> return Area.Invalida
    }
    val hectares = numero.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return Area.Invalida
    return Area.Valida(hectares)
}

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

    /**
     * Texto das coordenadas que não pedem confirmação mesmo fora do Brasil: as já confirmadas
     * pela pessoa e as que vieram do GPS ou do banco.
     */
    private var coordenadasConferidas: Pair<String, String>? = null

    init {
        if (propriedadeId != null) carregar(propriedadeId)
    }

    private fun carregar(id: String) {
        viewModelScope.launch {
            propriedadeRepository.buscarPorId(id)?.let { propriedade ->
                val latitude = propriedade.latitude.paraCampo()
                val longitude = propriedade.longitude.paraCampo()
                coordenadasConferidas = latitude to longitude
                _uiState.update {
                    it.copy(
                        nome = propriedade.nome,
                        cultura = propriedade.cultura,
                        area = propriedade.areaHectares.paraCampo(),
                        latitude = latitude,
                        longitude = longitude
                    )
                }
            }
        }
    }

    // Editar um campo limpa o erro correspondente.
    fun aoMudarNome(v: String) = _uiState.update { it.copy(nome = v, erroNome = null) }
    fun aoMudarCultura(v: String) = _uiState.update { it.copy(cultura = v) }
    fun aoMudarArea(v: String) = _uiState.update { it.copy(area = v, erroArea = null) }
    fun aoMudarLatitude(v: String) =
        _uiState.update { it.copy(latitude = v, erroCoordenadas = null, avisoCoordenadas = null) }

    fun aoMudarLongitude(v: String) =
        _uiState.update { it.copy(longitude = v, erroCoordenadas = null, avisoCoordenadas = null) }

    /** Botão ±: o teclado numérico de alguns aparelhos não tem o sinal de menos. */
    fun aoInverterSinalLatitude() = aoMudarLatitude(_uiState.value.latitude.comSinalInvertido())

    fun aoInverterSinalLongitude() = aoMudarLongitude(_uiState.value.longitude.comSinalInvertido())

    private fun String.comSinalInvertido(): String =
        trim().let { if (it.startsWith("-")) it.removePrefix("-") else "-$it" }

    /** Preenche as coordenadas obtidas do GPS com 6 casas decimais (~10 cm) e vírgula decimal. */
    fun aoUsarLocalizacao(latitude: Double, longitude: Double) {
        val textoLatitude = formatarCoordenada(latitude)
        val textoLongitude = formatarCoordenada(longitude)
        // Vieram do aparelho: fora do Brasil, a pessoa está mesmo lá.
        coordenadasConferidas = textoLatitude to textoLongitude
        _uiState.update {
            it.copy(
                latitude = textoLatitude,
                longitude = textoLongitude,
                erroCoordenadas = null,
                avisoCoordenadas = null
            )
        }
    }

    private fun formatarCoordenada(valor: Double): String =
        String.format(Locale.ROOT, "%.6f", valor).replace('.', ',')

    /** Formata para o campo: "18", "12,5", "-21,7333" (sem notação científica nem agrupamento). */
    private fun Double.paraCampo(): String = numeroBr(agrupar = false)

    /** Coordenada: aceita vírgula ou ponto decimal; rejeita NaN e infinito. */
    private fun String.paraNumero(): Double? =
        replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    fun aoSalvar() {
        val estado = _uiState.value
        // Ignora toques repetidos durante ou após a gravação.
        if (estado.salvando || estado.salvo) return

        val leituraDaArea = lerArea(estado.area)
        val area = (leituraDaArea as? Area.Valida)?.hectares
        val lat = estado.latitude.paraNumero()
        val lon = estado.longitude.paraNumero()

        var houveErro = false
        var novo = estado

        if (estado.nome.isBlank()) {
            novo = novo.copy(erroNome = "Informe o nome da propriedade.")
            houveErro = true
        }
        if (area == null) {
            val mensagem = if (leituraDaArea == Area.Ambigua) {
                "Não deu para entender a área. Use ponto só no milhar e vírgula nos decimais, como 1.250,5."
            } else {
                "A área deve ser um número maior que zero."
            }
            novo = novo.copy(erroArea = mensagem)
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

        // Aviso, não bloqueio: há sítios do outro lado da fronteira. O segundo toque confirma.
        val coordenadas = estado.latitude.trim() to estado.longitude.trim()
        if (!ficaNoBrasil(lat, lon) && coordenadas != coordenadasConferidas) {
            coordenadasConferidas = coordenadas
            _uiState.update { it.copy(avisoCoordenadas = AVISO_FORA_DO_BRASIL) }
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
        private const val AVISO_FORA_DO_BRASIL =
            "Essas coordenadas ficam fora do Brasil. Confira o sinal de menos: aqui a latitude e a " +
                "longitude costumam ser negativas. Se estiverem certas, toque em salvar de novo."

        /**
         * Retângulo que cobre o território brasileiro, ilhas oceânicas incluídas. Abrange faixas
         * dos países vizinhos: serve para pegar o sinal de menos esquecido, não para validar fronteira.
         */
        fun ficaNoBrasil(latitude: Double, longitude: Double): Boolean =
            latitude in -34.0..5.5 && longitude in -74.5..-28.5

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
