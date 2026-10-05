package br.joao.agrosaldo.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Uma exclusão que ainda pode voltar atrás. */
class ExclusaoRecente(
    /** "Lançamento excluído" — o texto da barra que aparece embaixo. */
    val mensagem: String,
    /** Grava de volta o que foi apagado. */
    val restaurar: suspend () -> Unit,
    /** Quando foi apagado, no relógio do [Desfazer]. */
    val criadaEm: Long
) {
    /** Já foi desfeita (ou está sendo). Impede restaurar duas vezes. */
    internal var usada = false
}

/**
 * "Excluiu sem querer? Desfazer."
 *
 * Em vez de perguntar "tem certeza?" antes de cada exclusão, o app apaga na
 * hora e oferece desfazer por alguns segundos. É o padrão do Gmail e do
 * próprio Android: menos um toque no caminho certo, e o erro tem conserto.
 *
 * Quem apaga (o ViewModel do formulário) chama [oferecer] com o jeito de
 * restaurar. Quem mostra a barra (a AppNavegacao) observa [pendente], porque
 * a tela do formulário já fechou quando a barra aparece.
 *
 * Só a exclusão mais recente pode ser desfeita: uma nova substitui a anterior,
 * que fica apagada de vez.
 */
class Desfazer(private val relogio: () -> Long = System::currentTimeMillis) {

    private val _pendente = MutableStateFlow<ExclusaoRecente?>(null)
    val pendente: StateFlow<ExclusaoRecente?> = _pendente.asStateFlow()

    fun oferecer(mensagem: String, restaurar: suspend () -> Unit) {
        _pendente.value = ExclusaoRecente(mensagem, restaurar, criadaEm = relogio())
    }

    /**
     * Passou do tempo da barra. Acontece quando o app foi fechado com a barra
     * na tela: ao reabrir, a oferta de horas atrás não deve reaparecer.
     */
    fun expirou(exclusao: ExclusaoRecente): Boolean = relogio() - exclusao.criadaEm > VALIDADE_MS

    /**
     * Restaura e só depois tira a oferta da tela.
     *
     * - NonCancellable: girar o celular no meio recria a tela que chamou;
     *   sem isto, uma propriedade voltaria só com parte dos lançamentos.
     * - Confere se [exclusao] ainda é a pendente e se já não foi usada: dois
     *   toques rápidos em "Desfazer" não restauram duas vezes.
     *
     * @return false se não deu para restaurar (a tela avisa a pessoa).
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

    /** Trocou de conta: o que foi apagado era da conta anterior e não volta mais. */
    fun esquecer() {
        _pendente.value = null
    }

    /** A barra sumiu sem o toque em "Desfazer": a exclusão fica valendo. */
    fun dispensar(exclusao: ExclusaoRecente) {
        if (_pendente.value === exclusao) _pendente.value = null
    }

    companion object {
        /** Um pouco mais que a barra longa do Material (10 s). */
        const val VALIDADE_MS = 15_000L
    }
}
