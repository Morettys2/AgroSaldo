package br.joao.agrosaldo.data

import br.joao.agrosaldo.domain.Despachantes
import br.joao.agrosaldo.domain.RelogioMonotonico
import br.joao.agrosaldo.domain.model.InstanteMonotonico
import br.joao.agrosaldo.domain.repository.UsuarioLogadoRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Relógio parado em [instante], no fuso [zona]. */
fun relogioFixo(instante: Instant, zona: ZoneId = ZoneOffset.UTC): Clock = Clock.fixed(instante, zona)

/** Relógio parado ao meio-dia de [dia]: o dia não vira por causa de fuso. */
fun relogioEm(dia: LocalDate, hora: LocalTime = LocalTime.NOON, zona: ZoneId = ZoneOffset.UTC): Clock =
    Clock.fixed(dia.atTime(hora).atZone(zona).toInstant(), zona)

/** Relógio cujo instante vem de [fonte] (epoch ms) a cada leitura: o teste muda uma variável e o tempo anda. */
fun relogioPorMs(zona: ZoneId = ZoneOffset.UTC, fonte: () -> Long): Clock = object : Clock() {
    override fun getZone(): ZoneId = zona

    override fun withZone(zone: ZoneId): Clock = relogioPorMs(zone, fonte)

    override fun instant(): Instant = Instant.ofEpochMilli(fonte())
}

/** Relógio que anda quando o teste manda: mexa em [instante] ou [fuso] e as leituras seguintes mudam. */
class RelogioMutavel(var instante: Instant, var fuso: ZoneId = ZoneOffset.UTC) : Clock() {

    constructor(dia: LocalDate, hora: LocalTime = LocalTime.NOON, zona: ZoneId = ZoneOffset.UTC) :
        this(dia.atTime(hora).atZone(zona).toInstant(), zona)

    /** Muda só o dia, mantendo a hora. */
    fun irParaODia(dia: LocalDate) {
        instante = dia.atTime(instante.atZone(fuso).toLocalTime()).atZone(fuso).toInstant()
    }

    override fun getZone(): ZoneId = fuso

    override fun withZone(zone: ZoneId): Clock = RelogioMutavel(instante, zone)

    override fun instant(): Instant = instante
}

/**
 * Relógio monotônico controlado pelo teste. [boot] nulo simula o sistema que não informa o boot.
 * Com [fonte], cada leitura vem da lambda (útil quando o teste já guarda o instante em variáveis).
 */
class RelogioMonotonicoFalso(var milissegundos: Long = 0L, var boot: Int? = 1, private val fonte: (() -> InstanteMonotonico?)? = null) :
    RelogioMonotonico {

    constructor(fonte: () -> InstanteMonotonico?) : this(0L, null, fonte)

    override fun milissegundos(): Long = fonte?.invoke()?.desdeOBootMs ?: milissegundos

    override fun instante(): InstanteMonotonico? = if (fonte !=
        null
    ) {
        fonte.invoke()
    } else {
        boot?.let { InstanteMonotonico(it, milissegundos) }
    }
}

/** Todos os tipos de trabalho no mesmo dispatcher: o teste controla quando tudo roda. */
class DespachantesDeTeste(dispatcher: CoroutineDispatcher) : Despachantes {
    override val calculo: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
}

/** Usuário conectado fixo, ou nenhum se [nome] for nulo. */
class UsuarioLogadoMemoria(private val nome: String? = null) : UsuarioLogadoRepository {
    override fun observarNome(): Flow<String?> = flowOf(nome)
}
