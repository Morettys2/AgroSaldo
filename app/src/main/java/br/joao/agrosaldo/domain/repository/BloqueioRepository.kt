package br.joao.agrosaldo.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Se o app pede a digital (ou o PIN do celular) ao abrir.
 *
 * É um ajuste DESTE aparelho, não da conta: quem liga é o dono do celular,
 * e o ajuste não vai junto no backup para outro celular.
 */
interface BloqueioRepository {
    val ativo: StateFlow<Boolean>
    fun definir(ativo: Boolean)
}
