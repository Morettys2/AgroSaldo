package br.joao.agrosaldo.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import br.joao.agrosaldo.domain.RelogioMonotonico
import br.joao.agrosaldo.domain.model.InstanteMonotonico

/** [RelogioMonotonico] sobre `SystemClock.elapsedRealtime` (inclui o tempo em deep sleep). */
class RelogioMonotonicoDoAndroid(contexto: Context) : RelogioMonotonico {

    private val resolver = contexto.applicationContext.contentResolver

    /** Contagem de boots; constante durante a vida do processo. null se o sistema não informa. */
    private val bootAtual: Int? by lazy {
        Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }
    }

    override fun milissegundos(): Long = SystemClock.elapsedRealtime()

    override fun instante(): InstanteMonotonico? = bootAtual?.let { InstanteMonotonico(it, SystemClock.elapsedRealtime()) }
}
