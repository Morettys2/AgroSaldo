package br.joao.agrosaldo.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Exclusão que ainda pode ser desfeita. */
class ExclusaoRecente(
    /** Texto exibido no snackbar. */
    val mensagem: String,
    /** Reinsere os dados removidos. */
    val restaurar: suspend () -> Unit,
    /** Instante da exclusão, na base de tempo do [Desfazer]. */
    val criadaEm: Long
) {
    /** Marca restauração iniciada; impede execução dupla. */
    internal var usada = false
}

/**
 * Desfazer de exclusões, compartilhado entre telas.
 *
 * O ViewModel que exclui chama [oferecer]; a AppNavegacao observa [pendente]
 * e exibe o snackbar, pois a tela de origem já pode ter sido fechada.
 * Só a exclusão mais recente é desfazível; uma nova oferta descarta a anterior.
 */
class Desfazer(private val relogio: () -> Long = System::currentTimeMillis) {

    private val _pendente = MutableStateFlow<ExclusaoRecente?>(null)
    val pendente: StateFlow<ExclusaoRecente?> = _pendente.asStateFlow()

    fun oferecer(mensagem: String, restaurar: suspend () -> Unit) {
        _pendente.value = ExclusaoRecente(mensagem, restaurar, criadaEm = relogio())
    }

    /** true se a oferta passou de [VALIDADE_MS] (ex.: app foi para segundo plano com o snackbar aberto). */
    fun expirou(exclusao: ExclusaoRecente): Boolean = relogio() - exclusao.criadaEm > VALIDADE_MS

    /**
     * Restaura [exclusao] e só então limpa [pendente].
     *
     * Roda em NonCancellable para não interromper a restauração se o escopo do
     * chamador for cancelado (ex.: mudança de configuração). Ignora ofertas que
     * não são a pendente ou que já foram usadas (toque duplo).
     *
     * @return false se a oferta não é válida ou a restauração falhou.
     */
    suspend fun desfazer(exclusao: ExclusaoRecente): Boolean {
        if (_pendente.value !== exclusao || exclusao.usada) return false
        exclusao.usada = true
        return try {
            withContext(NonCancellable) { exclusao.restaurar() }
            true
        } catch (e: Exception) {
            false
        } finally {
            if (_pendente.value === exclusao) _pendente.value = null
        }
    }

    /** Descarta a oferta pendente (troca de conta). */
    fun esquecer() {
        _pendente.value = null
    }

    /** Snackbar fechado sem desfazer: a exclusão se torna definitiva. */
    fun dispensar(exclusao: ExclusaoRecente) {
        if (_pendente.value === exclusao) _pendente.value = null
    }

    companion object {
        /** Margem sobre a duração do snackbar longo do Material (10 s). */
        const val VALIDADE_MS = 15_000L
    }
}
