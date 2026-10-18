package br.joao.agrosaldo.domain.conta

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash de senha com PBKDF2-HMAC-SHA256 e sal aleatório de 16 bytes por conta.
 * Usa apenas a JCA da plataforma, sem dependência externa.
 *
 * Formato gravado: `pbkdf2_sha256$<iterações>$<sal base64>$<hash base64>`
 * (Base64 padrão, sem padding). As iterações ficam no registro para permitir
 * aumentar o custo sem invalidar hashes antigos; ver [precisaAtualizar].
 */
class HashDeSenha(private val iteracoes: Int = ITERACOES_PADRAO, private val aleatorio: SecureRandom = SecureRandom()) {

    fun gerar(senha: String): String {
        val sal = bytesAleatorios(TAMANHO_SAL)
        return formatar(sal, derivar(senha, sal, iteracoes))
    }

    /**
     * Registro bem-formado, com as iterações atuais e sal e hash aleatórios, sem rodar o
     * PBKDF2. [confere] contra ele custa o mesmo que contra um hash real e, na prática,
     * nunca dá true (a chance de uma senha derivar o hash sorteado é de 2^-256).
     */
    fun ficticio(): String = formatar(bytesAleatorios(TAMANHO_SAL), bytesAleatorios(TAMANHO_HASH_BITS / 8))

    /**
     * true se [armazenado] usa menos iterações que o configurado. O rehash só
     * pode ser feito quando a senha em texto puro está disponível (login).
     */
    fun precisaAtualizar(armazenado: String): Boolean {
        val partes = armazenado.split(SEPARADOR)
        if (partes.size != 4 || partes[0] != ALGORITMO) return false
        val gravadas = partes[1].toIntOrNull() ?: return false
        return gravadas < iteracoes
    }

    /** true se [senha] corresponde a [armazenado]. Formato inválido retorna false. */
    fun confere(senha: String, armazenado: String): Boolean {
        val partes = armazenado.split(SEPARADOR)
        if (partes.size != 4 || partes[0] != ALGORITMO) return false

        val iteracoesGravadas = partes[1].toIntOrNull() ?: return false
        val sal = decodificar(partes[2]) ?: return false
        val esperado = decodificar(partes[3]) ?: return false

        val calculado = derivar(senha, sal, iteracoesGravadas)

        // Comparação em tempo constante para evitar timing attack.
        return MessageDigest.isEqual(calculado, esperado)
    }

    private fun derivar(senha: String, sal: ByteArray, iteracoes: Int): ByteArray {
        // NFKC (NIST 800-63B): a mesma senha com composição Unicode diferente gera o mesmo hash.
        val normalizada = Normalizer.normalize(senha, Normalizer.Form.NFKC)
        val especificacao = PBEKeySpec(normalizada.toCharArray(), sal, iteracoes, TAMANHO_HASH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITMO_JAVA).generateSecret(especificacao).encoded
        } finally {
            // Zera a cópia da senha mantida pelo PBEKeySpec.
            especificacao.clearPassword()
        }
    }

    private fun formatar(sal: ByteArray, hash: ByteArray): String = listOf(
        ALGORITMO,
        iteracoes.toString(),
        CODIFICADOR.encodeToString(sal),
        CODIFICADOR.encodeToString(hash)
    ).joinToString(SEPARADOR)

    private fun bytesAleatorios(tamanho: Int): ByteArray = ByteArray(tamanho).also { aleatorio.nextBytes(it) }

    private fun decodificar(texto: String): ByteArray? = try {
        DECODIFICADOR.decode(texto)
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        /**
         * 600k iterações: mínimo OWASP para PBKDF2-HMAC-SHA256. Custa de 0,5 a 1 s
         * em aparelho intermediário. Aumentar dispara rehash no próximo login.
         */
        const val ITERACOES_PADRAO = 600_000

        private const val ALGORITMO = "pbkdf2_sha256"
        private const val ALGORITMO_JAVA = "PBKDF2WithHmacSHA256"
        private const val SEPARADOR = "$"
        private const val TAMANHO_SAL = 16
        private const val TAMANHO_HASH_BITS = 256

        // Sem padding: mantém "$" como único caractere não Base64 do registro.
        private val CODIFICADOR: Base64.Encoder = Base64.getEncoder().withoutPadding()
        private val DECODIFICADOR: Base64.Decoder = Base64.getDecoder()
    }
}
