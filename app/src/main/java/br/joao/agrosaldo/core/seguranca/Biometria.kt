package br.joao.agrosaldo.core.seguranca

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Autenticação local via BiometricPrompt (biometria ou credencial do dispositivo).
 *
 * BIOMETRIC_WEAK é suficiente para desbloquear a UI (não há chave criptográfica atrelada;
 * o sistema limita tentativas). DEVICE_CREDENTIAL serve de fallback quando a biometria
 * não está disponível ou falha.
 */
object Biometria {

    private const val AUTENTICADORES = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** true se há biometria ou credencial de dispositivo cadastrada; pré-requisito do bloqueio. */
    fun disponivel(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Exibe o BiometricPrompt. Callbacks na main thread.
     *
     * [aoFalhar] recebe null em cancelamento pelo usuário e a mensagem localizada do
     * sistema nos demais erros (ex.: lockout). Leitura não reconhecida não é erro: o
     * próprio prompt permite nova tentativa.
     */
    fun autenticar(
        activity: FragmentActivity,
        titulo: String,
        subtitulo: String,
        aoSucesso: () -> Unit,
        aoFalhar: (mensagem: String?) -> Unit
    ) {
        val retorno = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                aoSucesso()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelou = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                aoFalhar(if (cancelou) null else errString.toString())
            }
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), retorno)

        // setNegativeButtonText é proibido quando DEVICE_CREDENTIAL está permitido.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .setAllowedAuthenticators(AUTENTICADORES)
            .build()

        prompt.authenticate(info)
    }

    /**
     * Pede a digital ou o PIN antes de ligar ou desligar o bloqueio do app: ligar garante
     * que a autenticação funciona (evita a pessoa se trancar fora); desligar impede que
     * terceiros removam a proteção. Erros do sistema (ex.: lockout) aparecem num Toast.
     */
    fun confirmarMudancaDoBloqueio(activity: FragmentActivity, ativar: Boolean, aoConfirmar: () -> Unit) {
        autenticar(
            activity = activity,
            titulo = if (ativar) "Ligar o bloqueio" else "Desligar o bloqueio",
            subtitulo = "Confirme com a digital ou o PIN do celular",
            aoSucesso = aoConfirmar,
            aoFalhar = { mensagem -> mensagem?.let { Toast.makeText(activity, it, Toast.LENGTH_LONG).show() } }
        )
    }
}

/** Percorre a cadeia de ContextWrapper (ex.: LocalContext do Compose) até a FragmentActivity. */
fun Context.acharFragmentActivity(): FragmentActivity? {
    var atual: Context = this
    while (atual is ContextWrapper) {
        if (atual is FragmentActivity) return atual
        atual = atual.baseContext
    }
    return null
}
