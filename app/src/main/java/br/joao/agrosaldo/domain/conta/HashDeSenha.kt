package br.joao.agrosaldo.domain.conta

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Protege a senha antes de ela ir para o banco.
 *
 * Por que não um hash simples (SHA-256 da senha)? Porque SHA-256 é rápido
 * demais: uma placa de vídeo testa bilhões por segundo, e senha de 8
 * caracteres cai em horas. O PBKDF2 repete o cálculo [iteracoes] vezes de
 * propósito — para quem tem a senha certa, custa uma fração de segundo;
 * para quem tenta adivinhar, custa essa fração multiplicada por bilhões.
 *
 * O "sal" é um valor aleatório diferente para cada conta. Com ele, duas
 * pessoas com a mesma senha ficam com hashes diferentes, e uma tabela de
 * senhas pré-calculadas não serve para nada.
 *
 * É a mesma ideia do bcrypt que se usa no NestJS. Aqui é PBKDF2 porque ele
 * já vem no Android e no Java, sem biblioteca extra.
 *
 * Formato gravado:  pbkdf2_sha256$iterações$sal$hash
 * Guardar as iterações junto permite aumentar o custo no futuro sem
 * invalidar as senhas antigas: [precisaAtualizar] detecta um hash antigo, e
 * o login grava um novo no lugar, com o custo atual.
 */
class HashDeSenha(
    private val iteracoes: Int = ITERACOES_PADRAO,
    private val aleatorio: SecureRandom = SecureRandom()
) {

    fun gerar(senha: String): String {
        val sal = ByteArray(TAMANHO_SAL).also { aleatorio.nextBytes(it) }
        val hash = derivar(senha, sal, iteracoes)
        return listOf(
            ALGORITMO,
            iteracoes.toString(),
            CODIFICADOR.encodeToString(sal),
            CODIFICADOR.encodeToString(hash)
        ).joinToString(SEPARADOR)
    }

    /**
     * true se [armazenado] foi gerado com menos iterações do que o padrão
     * atual. Só dá para refazer o hash no login, que é o único momento em
     * que o app tem a senha em mãos.
     */
    fun precisaAtualizar(armazenado: String): Boolean {
        val partes = armazenado.split(SEPARADOR)
        if (partes.size != 4 || partes[0] != ALGORITMO) return false
        val gravadas = partes[1].toIntOrNull() ?: return false
        return gravadas < iteracoes
    }

    /** true se [senha] é a que gerou [armazenado]. Formato estranho conta como senha errada. */
    fun confere(senha: String, armazenado: String): Boolean {
        val partes = armazenado.split(SEPARADOR)
        if (partes.size != 4 || partes[0] != ALGORITMO) return false

        val iteracoesGravadas = partes[1].toIntOrNull() ?: return false
        val sal = decodificar(partes[2]) ?: return false
        val esperado = decodificar(partes[3]) ?: return false

        val calculado = derivar(senha, sal, iteracoesGravadas)

        // MessageDigest.isEqual compara em tempo constante. Um "==" comum
        // para no primeiro byte diferente, e medir esse tempo vaza quanto
        // do hash o atacante já acertou.
        return MessageDigest.isEqual(calculado, esperado)
    }

    private fun derivar(senha: String, sal: ByteArray, iteracoes: Int): ByteArray {
        // NFKC: o mesmo "ç" pode chegar como um caractere só ou como "c" +
        // cedilha, dependendo do teclado. Sem normalizar, a mesma senha
        // digitada em outro celular daria outro hash. (Recomendação NIST 800-63B.)
        val normalizada = Normalizer.normalize(senha, Normalizer.Form.NFKC)
        val especificacao = PBEKeySpec(normalizada.toCharArray(), sal, iteracoes, TAMANHO_HASH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITMO_JAVA).generateSecret(especificacao).encoded
        } finally {
            // Apaga a cópia da senha da memória assim que não precisa mais dela.
            especificacao.clearPassword()
        }
    }

    private fun decodificar(texto: String): ByteArray? = try {
        DECODIFICADOR.decode(texto)
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        /**
         * 600 mil repetições: o mínimo que a OWASP recomenda para
         * PBKDF2-HMAC-SHA256 (Password Storage Cheat Sheet). Num celular
         * intermediário leva algo entre meio e um segundo — só no login e no
         * cadastro, com o indicador de carregando na tela.
         */
        const val ITERACOES_PADRAO = 600_000

        private const val ALGORITMO = "pbkdf2_sha256"
        private const val ALGORITMO_JAVA = "PBKDF2WithHmacSHA256"
        private const val SEPARADOR = "$"
        private const val TAMANHO_SAL = 16
        private const val TAMANHO_HASH_BITS = 256

        // Base64 sem o "=" do fim: o "$" continua sendo o único separador.
        private val CODIFICADOR: Base64.Encoder = Base64.getEncoder().withoutPadding()
        private val DECODIFICADOR: Base64.Decoder = Base64.getDecoder()
    }
}
