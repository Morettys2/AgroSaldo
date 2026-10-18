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
 * Leitura única da posição atual, em primeiro plano, para preencher coordenadas da
 * propriedade. Sem rastreamento contínuo nem histórico.
 *
 * Usa LocationManager via androidx.core para não depender do Google Play Services.
 */
object LocalizacaoDoCelular {

    fun temPermissao(context: Context): Boolean = concedida(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        concedida(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * Callbacks na main thread; falhas chegam como mensagem pronta para o usuário.
     * @return sinal para cancelar a busca, ou null se ela não chegou a iniciar.
     */
    @Suppress("SwallowedException") // sem permissão vira mensagem para a pessoa, não é falha do app
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
            // Permissão revogada entre a checagem e a chamada.
            aoFalhar("Sem permissão de localização. Você ainda pode digitar as coordenadas.")
            null
        }
    }

    /** API 31+: FUSED_PROVIDER (funciona com permissão aproximada). Antes: GPS se houver permissão precisa, senão rede. */
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
