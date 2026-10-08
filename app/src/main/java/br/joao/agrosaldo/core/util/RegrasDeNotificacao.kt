package br.joao.agrosaldo.core.util

/** O que o botão "Permitir notificações" abre. */
enum class DestinoDaPermissao {
    /** Diálogo de permissão do sistema (Android 13+). */
    DIALOGO,

    /** Ajustes de notificação do app: notificações do app desligadas ou permissão negada de vez. */
    AJUSTES_DO_APP,

    /** Ajustes do canal do aviso de chuva, desligado com o resto do app liberado. */
    AJUSTES_DO_CANAL
}

/** Regras do botão "Permitir notificações", fora do Compose para serem testadas. */
object RegrasDeNotificacao {

    /**
     * @param permissaoPendente Android 13+ sem POST_NOTIFICATIONS concedida.
     * @return null se já é possível notificar.
     */
    fun destino(permissaoPendente: Boolean, appLigado: Boolean, canalLigado: Boolean): DestinoDaPermissao? = when {
        permissaoPendente -> DestinoDaPermissao.DIALOGO
        !appLigado -> DestinoDaPermissao.AJUSTES_DO_APP
        !canalLigado -> DestinoDaPermissao.AJUSTES_DO_CANAL
        else -> null
    }

    /**
     * true se a recusa veio sem o diálogo aparecer (permissão negada de vez): só os ajustes
     * resolvem. Uma recusa feita no diálogo muda `shouldShowRequestPermissionRationale`
     * (false → true na 1ª, true → false na 2ª); fechar o diálogo sem responder não muda, por
     * isso o 1º pedido no aparelho ([jaPediuAntes] false) nunca leva aos ajustes.
     */
    fun dialogoBloqueado(aceitou: Boolean, explicarAntes: Boolean, explicarDepois: Boolean, jaPediuAntes: Boolean): Boolean =
        !aceitou && !explicarAntes && !explicarDepois && jaPediuAntes
}
