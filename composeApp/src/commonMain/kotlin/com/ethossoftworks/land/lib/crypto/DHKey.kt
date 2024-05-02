package com.ethossoftworks.land.lib.crypto

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import com.ionspin.kotlin.bignum.integer.toBigInteger
import korlibs.crypto.SecureRandom

// Diffie-Hellman Cyrptographic key generator
object DHKey {
    // Known 2048-bit prime (taken from RFC 3526)
    private val p = """
        FFFFFFFF FFFFFFFF C90FDAA2 2168C234 C4C6628B 80DC1CD1
        29024E08 8A67CC74 020BBEA6 3B139B22 514A0879 8E3404DD
        EF9519B3 CD3A431B 302B0A6D F25F1437 4FE1356D 6D51C245
        E485B576 625E7EC6 F44C42E9 A637ED6B 0BFF5CB6 F406B7ED
        EE386BFB 5A899FA5 AE9F2411 7C4B1FE6 49286651 ECE45B3D
        C2007CB8 A163BF05 98DA4836 1C55D39A 69163FA8 FD24CF5F
        83655D23 DCA3AD96 1C62F356 208552BB 9ED52907 7096966D
        670C354E 4ABC9804 F1746C08 CA18217C 32905E46 2E36CE3B
        E39E772C 180E8603 9B2783A2 EC07A28F B5C55DF0 6F4C52C9
        DE2BCBF6 95581718 3995497C EA956AE5 15D22618 98FA0510
        15728E5A 8AACAA68 FFFFFFFF FFFFFFFF"""
        .replace(" ", "")
        .replace("\n", "")
        .toBigInteger(16)

    private val g = BigInteger.TWO

    // Generate a random private key in the range [1, p - 1]
    fun generatePrivateKey(): BigInteger {
        val range = p - BigInteger.ONE
        var privateKey: BigInteger
        do {
            val randomBytes = SecureRandom.nextBytes(256) // 256 bytes = 2048 bits
            privateKey = BigInteger.fromByteArray(randomBytes, Sign.POSITIVE)
        } while (privateKey <= BigInteger.ZERO || privateKey >= range)
        return privateKey
    }

    fun computePublicKey(privateKey: BigInteger): BigInteger {
        return g.powMod(privateKey, p)
    }

    fun computeSharedKeyBytes(publicKey: BigInteger, privateKey: BigInteger): ByteArray {
        return computeSharedKey(publicKey, privateKey).toByteArray()
    }

    fun computeSharedKey(publicKey: BigInteger, privateKey: BigInteger): BigInteger {
        return publicKey.powMod(privateKey, p)
    }

    fun keyToBytes(key: BigInteger) = key.toByteArray()
    fun keyFromBytes(bytes: ByteArray) = BigInteger.fromByteArray(bytes, Sign.POSITIVE)

    private fun BigInteger.powMod(exponent: BigInteger, modulus: BigInteger): BigInteger {
        var result = BigInteger.ONE
        var base = this % modulus
        var exp = exponent

        while (exp > BigInteger.ZERO) {
            if (exp.isOdd) {
                result = (result * base) % modulus
            }
            base = (base * base) % modulus
            exp /= 2.toBigInteger()
        }

        return result
    }

    private val BigInteger.isOdd: Boolean
        get() = this % 2.toBigInteger() == BigInteger.ONE
}