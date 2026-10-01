package br.joao.agrosaldo.core.localizacao

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * Pega a posição atual do celular UMA vez, para preencher as coordenadas do
 * sítio. Não acompanha o usuário, não grava histórico e não roda em segundo
 * plano — só quando a pessoa toca no botão.
 *
 * Usa o LocationManager do próprio Android (via androidx.core), sem a
 * biblioteca do Google Play Services: funciona em qualquer celular e não
 * acrescenta dependência.
 */
object LocalizacaoDoCelular {

    fun temPermissao(context: Context): Boolean =
        concedida(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            concedida(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * Devolve o sinal de cancelamento da busca (ou null se nem começou), para
     * a tela cancelar se for fechada antes de o GPS responder.
     */
    fun obter(
        context: Context,
        aoEncontrar: (latitude: Double, longitude: Double) -> Unit,
        aoFalhar: (mensagem: String) -> Unit
    ): CancellationSignal? {
        val gerenciador = ContextCompat.getSystemService(context, LocationManager::class.java)
        if (gerenciador == null || !LocationManagerCompat.isLocationEnabled(gerenciador)) {
            aoFalhar("A localização do celular está desligada. Ligue nas configurações rápidas e tente de novo.")
            return null
        }
        if (!temPermissao(context)) {
            aoFalhar("Sem permissão de localização. Você ainda pode digitar as coordenadas.")
            return null
        }

        val provedor = escolherProvedor(context, gerenciador)
        if (provedor == null) {
            aoFalhar("Nenhuma fonte de localização disponível agora. Digite as coordenadas.")
            return null
        }

        val cancelamento = CancellationSignal()
        return try {
            LocationManagerCompat.getCurrentLocation(
                gerenciador,
                provedor,
                cancelamento,
                ContextCompat.getMainExecutor(context)
            ) { local ->
                if (local == null) {
                    aoFalhar("Não deu para achar a localização agora. Tente num lugar aberto, ou digite as coordenadas.")
                } else {
                    aoEncontrar(local.latitude, local.longitude)
                }
            }
            cancelamento
        } catch (e: SecurityException) {
            // A permissão pode ter sido retirada entre a checagem e o pedido.
            aoFalhar("Sem permissão de localização. Você ainda pode digitar as coordenadas.")
            null
        }
    }

    /**
     * Android 12+: o provedor "fused" junta GPS, Wi-Fi e rede e funciona até
     * com a permissão aproximada. Antes disso: GPS se a permissão for
     * precisa, senão a rede.
     */
    private fun escolherProvedor(context: Context, gerenciador: LocationManager): String? {
        val precisa = concedida(context, Manifest.permission.ACCESS_FINE_LOCATION)
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                gerenciador.allProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            precisa && gerenciador.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            gerenciador.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    private fun concedida(context: Context, permissao: String): Boolean =
        ContextCompat.checkSelfPermission(context, permissao) == PackageManager.PERMISSION_GRANTED
}
