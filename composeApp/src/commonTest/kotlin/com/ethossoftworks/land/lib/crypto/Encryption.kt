package com.ethossoftworks.land.lib.crypto

import korlibs.crypto.AES
import korlibs.crypto.CipherPadding
import korlibs.crypto.SecureRandom
import kotlin.test.Test
import kotlin.test.assertTrue

class Encryption {
    @Test
    fun encryption() {
        val alicePrivateKey = DHKey.generatePrivateKey(2_048)
        val bobPrivateKey = DHKey.generatePrivateKey(2_048)
        val bobPublicKey = DHKey.computePublicKey(bobPrivateKey)
        val sharedKey = DHKey.computeSharedSecret(bobPublicKey, alicePrivateKey)

        val iv = SecureRandom.nextBytes(16)

        val original = byteArrayOf(0x00.toByte(), 0x01.toByte(), 0x02.toByte())
        val encrypted = AES.encryptAesCtr(original, sharedKey, iv, CipherPadding.PKCS7Padding)
        val decrypted = AES.decryptAesCtr(encrypted, sharedKey, iv, CipherPadding.PKCS7Padding)

        assertTrue { decrypted.contentEquals(original) }
    }
}