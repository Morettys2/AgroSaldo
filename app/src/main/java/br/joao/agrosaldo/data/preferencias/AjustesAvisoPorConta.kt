package br.joao.agrosaldo.data.preferencias

import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.repository.AjustesAviso
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Chave-valor onde [AjustesAvisoPorConta] guarda os dados: SharedPreferences no app, um mapa
 * nos testes. Valores Boolean, Int, Long, Float ou Set<String>.
 */
interface ArmazenamentoDeAjustes {
    fun todos(): Map<String, Any?>

    /** Aplica gravações e remoções numa única edição. */
    fun editar(gravar: Map<String, Any>, remover: Collection<String> = emptyList())
}

/**
 * [AjustesAvisoRepository] separado por conta: as chaves levam o prefixo `<usuarioId>:` e
 * [ajustes] acompanha a conta conectada em [sessao] (sem sessão, valem os padrões). O último
 * aviso de cada propriedade fica em `<usuarioId>:ultimo_aviso_<propriedadeId>` (epoch ms).
 *
 * As chaves sem prefixo da versão anterior, quando os ajustes valiam para o aparelho, são
 * adotadas pela primeira conta carregada, para quem já usava o app não perder o que configurou.
 *
 * @param aoEntrarEmConta chamado com os ajustes de cada conta que entra na sessão depois da
 *   criação (login ou troca de conta).
 */
class AjustesAvisoPorConta(
    private val armazenamento: ArmazenamentoDeAjustes,
    private val sessao: SessaoRepository,
    private val aoEntrarEmConta: (AjustesAviso) -> Unit = {}
) : AjustesAvisoRepository {

    private var contaCarregada: String? = sessao.usuarioId.value

    private val _ajustes = MutableStateFlow(ler(contaCarregada))
    override val ajustes: StateFlow<AjustesAviso> = _ajustes.asStateFlow()

    init {
        // Unconfined: a troca de conta recarrega os ajustes logo em seguida, na própria thread
        // que a fez. A coleta vive enquanto o repositório viver.
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).launch {
            sessao.usuarioId.collect { recarregar(it) }
        }
    }

    @Synchronized
    override fun salvar(ajustes: AjustesAviso) {
        val prefixo = prefixo(sessao.usuarioId.value ?: return)
        armazenamento.editar(
            mapOf(
                prefixo + CHAVE_ATIVO to ajustes.ativo,
                prefixo + CHAVE_PROBABILIDADE to ajustes.limites.probabilidadeMinima,
                prefixo + CHAVE_CHUVA to ajustes.limites.chuvaMinimaMm.toFloat(),
                prefixo + CHAVE_JANELA to ajustes.limites.janelaHoras,
                prefixo + CHAVE_SEM_AVISO to ajustes.propriedadesSemAviso.toSet()
            )
        )
        _ajustes.value = ajustes
    }

    override fun ultimoAvisoEm(propriedadeId: String): Instant? {
        val usuarioId = sessao.usuarioId.value ?: return null
        val ms = armazenamento.todos()[chaveDoUltimoAviso(usuarioId, propriedadeId)] as? Long ?: return null
        return Instant.ofEpochMilli(ms)
    }

    @Synchronized
    override fun registrarAviso(propriedadeId: String, em: Instant) {
        val usuarioId = sessao.usuarioId.value ?: return
        armazenamento.editar(mapOf(chaveDoUltimoAviso(usuarioId, propriedadeId) to em.toEpochMilli()))
    }

    @Synchronized
    override fun esquecerAvisosExceto(existentes: Set<String>) {
        val prefixo = prefixo(sessao.usuarioId.value ?: return) + PREFIXO_ULTIMO
        val orfas = armazenamento.todos().keys.filter { it.startsWith(prefixo) && it.removePrefix(prefixo) !in existentes }
        if (orfas.isNotEmpty()) armazenamento.editar(emptyMap(), remover = orfas)
    }

    @Synchronized
    override fun excluirDadosDoUsuario(usuarioId: String) {
        val prefixo = prefixo(usuarioId)
        val chaves = armazenamento.todos().keys.filter { it.startsWith(prefixo) }
        if (chaves.isNotEmpty()) armazenamento.editar(emptyMap(), remover = chaves)
        if (contaCarregada == usuarioId) _ajustes.value = AjustesAviso()
    }

    private fun recarregar(usuarioId: String?) {
        val (entrou, daConta) = synchronized(this) {
            val outraConta = usuarioId != null && usuarioId != contaCarregada
            contaCarregada = usuarioId
            _ajustes.value = ler(usuarioId)
            outraConta to _ajustes.value
        }
        if (entrou) aoEntrarEmConta(daConta)
    }

    private fun ler(usuarioId: String?): AjustesAviso {
        if (usuarioId == null) return AjustesAviso()
        adotarChavesSemConta(usuarioId)
        val prefixo = prefixo(usuarioId)
        val todos = armazenamento.todos()
        val padrao = LimitesChuva()
        // Valores fora da faixa fazem o init de LimitesChuva lançar; volta ao padrão.
        val limites = runCatching {
            LimitesChuva(
                probabilidadeMinima = todos[prefixo + CHAVE_PROBABILIDADE] as? Int ?: padrao.probabilidadeMinima,
                chuvaMinimaMm = (todos[prefixo + CHAVE_CHUVA] as? Float)?.toDouble() ?: padrao.chuvaMinimaMm,
                janelaHoras = todos[prefixo + CHAVE_JANELA] as? Int ?: padrao.janelaHoras
            )
        }.getOrDefault(padrao)
        return AjustesAviso(
            ativo = todos[prefixo + CHAVE_ATIVO] as? Boolean ?: true,
            limites = limites,
            propriedadesSemAviso = (todos[prefixo + CHAVE_SEM_AVISO] as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
        )
    }

    /** Passa para [usuarioId] as chaves sem prefixo, sem sobrescrever o que a conta já gravou. */
    private fun adotarChavesSemConta(usuarioId: String) {
        val todos = armazenamento.todos()
        val semConta = todos.filterKeys { it in CHAVES_DE_AJUSTE || it.startsWith(PREFIXO_ULTIMO) }
        if (semConta.isEmpty()) return
        val prefixo = prefixo(usuarioId)
        val adotadas = buildMap {
            for ((chave, valor) in semConta) {
                if (valor != null && prefixo + chave !in todos) put(prefixo + chave, valor)
            }
        }
        armazenamento.editar(adotadas, remover = semConta.keys)
    }

    private fun prefixo(usuarioId: String) = "$usuarioId:"

    private fun chaveDoUltimoAviso(usuarioId: String, propriedadeId: String) = prefixo(usuarioId) + PREFIXO_ULTIMO + propriedadeId

    private companion object {
        const val CHAVE_ATIVO = "ativo"
        const val CHAVE_PROBABILIDADE = "probabilidade_minima"
        const val CHAVE_CHUVA = "chuva_minima_mm"
        const val CHAVE_JANELA = "janela_horas"
        const val CHAVE_SEM_AVISO = "propriedades_sem_aviso"
        const val PREFIXO_ULTIMO = "ultimo_aviso_"
        val CHAVES_DE_AJUSTE = setOf(CHAVE_ATIVO, CHAVE_PROBABILIDADE, CHAVE_CHUVA, CHAVE_JANELA, CHAVE_SEM_AVISO)
    }
}
