package br.joao.agrosaldo.feature.propriedade

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.joao.agrosaldo.core.navigation.Rotas
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.numeroBr
import br.joao.agrosaldo.core.util.tentar
import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val excluido: Boolean = false,
    /** A propriedade em edição não existe mais (ex.: foi excluída); a tela avisa e fecha. */
    val naoEncontrado: Boolean = false
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

    /** Número válido, mas acima de [Propriedade.AREA_MAXIMA_HECTARES]: provável zero a mais. */
    data object AcimaDoLimite : Area
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
        else -> null
    }
    val hectares = numero?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    return when {
        numero == null -> if (SO_DIGITOS_E_SEPARADORES.matches(limpo)) Area.Ambigua else Area.Invalida
        hectares == null -> Area.Invalida
        hectares > Propriedade.AREA_MAXIMA_HECTARES -> Area.AcimaDoLimite
        else -> Area.Valida(hectares)
    }
}

/** Os números já lidos e conferidos, prontos para virar uma [Propriedade]. */
private data class EntradasValidas(val area: Double, val latitude: Double, val longitude: Double)

/** Resultado de conferir o formulário: as entradas lidas ou o estado com os erros marcados. */
private sealed interface Validacao {
    data class Valida(val entradas: EntradasValidas) : Validacao

    data class Invalida(val estado: PropriedadeFormUiState) : Validacao
}

/**
 * Formulário de criação/edição de propriedade. Toda a validação fica aqui; a tela só
 * exibe os erros do estado.
 *
 * @param propriedadeId id em edição; nulo para criação.
 * @param estadoSalvo guarda os campos digitados para sobreviverem à morte do processo; na
 *   edição, o que foi restaurado vale mais que o que está no banco.
 */
@HiltViewModel
class PropriedadeFormViewModel(
    private val propriedadeRepository: PropriedadeRepository,
    /** Propriedade e mês escolhidos, compartilhados com as outras telas. */
    private val selecao: SelecaoAtual,
    private val propriedadeId: String?,
    private val desfazer: Desfazer = Desfazer(),
    private val estadoSalvo: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    /** Construtor do Hilt: o id da propriedade em edição vem do argumento da rota (nulo na criação). */
    @Inject
    constructor(
        propriedadeRepository: PropriedadeRepository,
        selecao: SelecaoAtual,
        desfazer: Desfazer,
        estadoSalvo: SavedStateHandle
    ) : this(
        propriedadeRepository = propriedadeRepository,
        selecao = selecao,
        propriedadeId = estadoSalvo.get<String>(Rotas.ARG_ID),
        desfazer = desfazer,
        estadoSalvo = estadoSalvo
    )

    private val _uiState = MutableStateFlow(
        PropriedadeFormUiState(
            nome = restaurado(CHAVE_NOME).orEmpty(),
            cultura = restaurado(CHAVE_CULTURA).orEmpty(),
            area = restaurado(CHAVE_AREA).orEmpty(),
            latitude = restaurado(CHAVE_LATITUDE).orEmpty(),
            longitude = restaurado(CHAVE_LONGITUDE).orEmpty(),
            ehEdicao = propriedadeId != null
        )
    )
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
            val propriedade = propriedadeRepository.buscarPorId(id)
            if (propriedade == null) {
                // Salvar recriaria o registro com o id antigo.
                _uiState.update { it.copy(naoEncontrado = true) }
                return@launch
            }
            val latitude = propriedade.latitude.paraCampo()
            val longitude = propriedade.longitude.paraCampo()
            coordenadasConferidas = latitude to longitude
            // O que foi restaurado (ou digitado antes de carregar) vale mais que o banco.
            _uiState.update {
                it.copy(
                    nome = restaurado(CHAVE_NOME) ?: propriedade.nome,
                    cultura = restaurado(CHAVE_CULTURA) ?: propriedade.cultura,
                    area = restaurado(CHAVE_AREA) ?: propriedade.areaHectares.paraCampo(),
                    latitude = restaurado(CHAVE_LATITUDE) ?: latitude,
                    longitude = restaurado(CHAVE_LONGITUDE) ?: longitude
                )
            }
        }
    }

    private fun restaurado(chave: String): String? = estadoSalvo.get<String>(chave)

    // Editar um campo guarda o texto em [estadoSalvo] e limpa o erro correspondente.
    fun aoMudarNome(v: String) {
        val texto = v.take(Propriedade.NOME_MAXIMO)
        estadoSalvo[CHAVE_NOME] = texto
        _uiState.update { it.copy(nome = texto, erroNome = null) }
    }

    fun aoMudarCultura(v: String) {
        val texto = v.take(Propriedade.CULTURA_MAXIMA)
        estadoSalvo[CHAVE_CULTURA] = texto
        _uiState.update { it.copy(cultura = texto) }
    }

    fun aoMudarArea(v: String) {
        estadoSalvo[CHAVE_AREA] = v
        _uiState.update { it.copy(area = v, erroArea = null) }
    }

    fun aoMudarLatitude(v: String) {
        estadoSalvo[CHAVE_LATITUDE] = v
        _uiState.update { it.copy(latitude = v, erroCoordenadas = null, avisoCoordenadas = null) }
    }

    fun aoMudarLongitude(v: String) {
        estadoSalvo[CHAVE_LONGITUDE] = v
        _uiState.update { it.copy(longitude = v, erroCoordenadas = null, avisoCoordenadas = null) }
    }

    /** Botão ±: o teclado numérico de alguns aparelhos não tem o sinal de menos. */
    fun aoInverterSinalLatitude() = aoMudarLatitude(_uiState.value.latitude.comSinalInvertido())

    fun aoInverterSinalLongitude() = aoMudarLongitude(_uiState.value.longitude.comSinalInvertido())

    private fun String.comSinalInvertido(): String = trim().let { if (it.startsWith("-")) it.removePrefix("-") else "-$it" }

    /** Preenche as coordenadas obtidas do GPS com 6 casas decimais (~10 cm) e vírgula decimal. */
    fun aoUsarLocalizacao(latitude: Double, longitude: Double) {
        val textoLatitude = formatarCoordenada(latitude)
        val textoLongitude = formatarCoordenada(longitude)
        // Vieram do aparelho: fora do Brasil, a pessoa está mesmo lá.
        coordenadasConferidas = textoLatitude to textoLongitude
        estadoSalvo[CHAVE_LATITUDE] = textoLatitude
        estadoSalvo[CHAVE_LONGITUDE] = textoLongitude
        _uiState.update {
            it.copy(
                latitude = textoLatitude,
                longitude = textoLongitude,
                erroCoordenadas = null,
                avisoCoordenadas = null
            )
        }
    }

    private fun formatarCoordenada(valor: Double): String = String.format(Locale.ROOT, "%.6f", valor).replace('.', ',')

    /** Formata para o campo: "18", "12,5", "-21,7333" (sem notação científica nem agrupamento). */
    private fun Double.paraCampo(): String = numeroBr(agrupar = false)

    /** Coordenada: aceita vírgula ou ponto decimal; rejeita NaN e infinito. */
    private fun String.paraNumero(): Double? = replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    fun aoSalvar() {
        val estado = _uiState.value
        // Ignora toques repetidos durante ou após a gravação.
        if (estado.salvando || estado.salvo || estado.naoEncontrado) return

        when (val validacao = validar(estado)) {
            is Validacao.Invalida -> _uiState.value = validacao.estado
            is Validacao.Valida -> gravar(estado, validacao.entradas)
        }
    }

    /** Confere os campos: devolve as entradas lidas ou o estado com a mensagem de cada campo errado. */
    private fun validar(estado: PropriedadeFormUiState): Validacao {
        val leituraDaArea = lerArea(estado.area)
        val area = (leituraDaArea as? Area.Valida)?.hectares
        val lat = estado.latitude.paraNumero()
        val lon = estado.longitude.paraNumero()
        val coordenadas = coordenadasValidas(lat, lon)
        val nomeValido = estado.nome.isNotBlank()

        if (nomeValido && area != null && coordenadas != null) {
            return Validacao.Valida(EntradasValidas(area, coordenadas.first, coordenadas.second))
        }
        return Validacao.Invalida(
            estado.copy(
                erroNome = if (nomeValido) estado.erroNome else "Informe o nome da propriedade.",
                erroArea = if (area == null) mensagemDaArea(leituraDaArea) else estado.erroArea,
                erroCoordenadas = if (coordenadas == null) mensagemDasCoordenadas(lat, lon) else estado.erroCoordenadas
            )
        )
    }

    private fun coordenadasValidas(lat: Double?, lon: Double?): Pair<Double, Double>? = when {
        lat == null || lon == null -> null
        lat in Propriedade.LATITUDES && lon in Propriedade.LONGITUDES -> lat to lon
        else -> null
    }

    private fun mensagemDaArea(leitura: Area): String = when (leitura) {
        Area.Ambigua -> "Não deu para entender a área. Use ponto só no milhar e vírgula nos decimais, como 1.250,5."
        Area.AcimaDoLimite -> "A área máxima é 1.000.000 ha. Confira se não sobrou algum zero."
        else -> "A área deve ser um número maior que zero."
    }

    private fun mensagemDasCoordenadas(lat: Double?, lon: Double?): String =
        if (lat == null || lon == null) "Latitude e longitude precisam ser números." else "Coordenadas fora do intervalo válido."

    /** Grava a propriedade; fora do Brasil, antes avisa e só grava no segundo toque (confirmação). */
    private fun gravar(estado: PropriedadeFormUiState, entradas: EntradasValidas) {
        // Aviso, não bloqueio: há sítios do outro lado da fronteira. O segundo toque confirma.
        val coordenadas = estado.latitude.trim() to estado.longitude.trim()
        if (!ficaNoBrasil(entradas.latitude, entradas.longitude) && coordenadas != coordenadasConferidas) {
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
                    areaHectares = entradas.area,
                    latitude = entradas.latitude,
                    longitude = entradas.longitude
                )
                propriedadeRepository.salvar(propriedade)
                selecao.selecionar(propriedade.id)
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
     * Exclui a propriedade e seus lançamentos (a tela pede confirmação antes).
     * Oferece "Desfazer", que restaura tudo com os mesmos ids.
     */
    fun aoExcluir() {
        val id = propriedadeId ?: return
        viewModelScope.launch {
            // Exclusão transacional; retorna os dados removidos para o "Desfazer".
            tentar { propriedadeRepository.excluirComLancamentos(id) }
                .onSuccess { excluida ->
                    selecao.limparSeFor(id)
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
        /** Aviso exibido quando [PropriedadeFormUiState.naoEncontrado]. */
        const val AVISO_NAO_ENCONTRADO = "Esta propriedade não existe mais."

        // Chaves do SavedStateHandle; os valores são o texto dos campos.
        internal const val CHAVE_NOME = "nome"
        internal const val CHAVE_CULTURA = "cultura"
        internal const val CHAVE_AREA = "area"
        internal const val CHAVE_LATITUDE = "latitude"
        internal const val CHAVE_LONGITUDE = "longitude"

        private const val AVISO_FORA_DO_BRASIL =
            "Essas coordenadas ficam fora do Brasil. Confira o sinal de menos: aqui a latitude e a " +
                "longitude costumam ser negativas. Se estiverem certas, toque em salvar de novo."

        private val LATITUDES_DO_BRASIL: ClosedFloatingPointRange<Double> = -34.0..5.5
        private val LONGITUDES_DO_BRASIL: ClosedFloatingPointRange<Double> = -74.5..-28.5

        /**
         * Retângulo que cobre o território brasileiro, ilhas oceânicas incluídas. Abrange faixas
         * dos países vizinhos: serve para pegar o sinal de menos esquecido, não para validar fronteira.
         */
        fun ficaNoBrasil(latitude: Double, longitude: Double): Boolean =
            latitude in LATITUDES_DO_BRASIL && longitude in LONGITUDES_DO_BRASIL
    }
}
