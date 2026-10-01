package br.joao.agrosaldo.core.seguranca

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Digital, rosto ou o PIN/padrão/senha do próprio celular.
 *
 * - BIOMETRIC_WEAK aceita qualquer biometria que o Android reconhece,
 *   inclusive o desbloqueio facial dos celulares mais simples. Para
 *   destravar a tela do app basta; força bruta é barrada pelo próprio sistema.
 * - DEVICE_CREDENTIAL deixa usar o PIN do celular. Sem ele, quem machuca o
 *   dedo na lida ficaria trancado fora do app.
 *
 * O app nunca vê a digital nem o PIN: o Android só responde "é o dono" ou
 * "não é".
 */
object Biometria {

    private const val AUTENTICADORES = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** O celular tem digital ou PIN cadastrado? Se não tiver, não dá para travar o app. */
    fun disponivel(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Mostra a janela do sistema.
     *
     * [aoFalhar] recebe null quando a própria pessoa cancelou (não há o que
     * avisar) e uma mensagem do Android, já em português, nos outros casos
     * — por exemplo, "Muitas tentativas".
     *
     * Dedo não reconhecido não chega aqui: a própria janela avisa e deixa
     * tentar de novo.
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

        // Com DEVICE_CREDENTIAL não existe botão "Cancelar" próprio: o
        // sistema oferece "Usar PIN" no lugar. Por isso não há setNegativeButtonText.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .setAllowedAuthenticators(AUTENTICADORES)
            .build()

        prompt.authenticate(info)
    }
}

/**
 * Dentro do Compose, LocalContext pode vir "embrulhado" (um ContextWrapper
 * com outro tema, por exemplo). Desembrulha até achar a Activity.
 */
fun Context.acharFragmentActivity(): FragmentActivity? {
    var atual: Context = this
    while (atual is ContextWrapper) {
        if (atual is FragmentActivity) return atual
        atual = atual.baseContext
    }
    return null
}
