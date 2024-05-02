package com.ethossoftworks.land.lib.crypto

import korlibs.crypto.AES
import korlibs.crypto.CipherPadding
import korlibs.crypto.SecureRandom
import kotlin.test.Test
import kotlin.test.assertTrue

class Encryption {
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun encryption() {
        val alicePrivateKey = DHKey.generatePrivateKey()
        val bobPrivateKey = DHKey.generatePrivateKey()
        val bobPublicKey = DHKey.computePublicKey(bobPrivateKey)
        val sharedKey = DHKey.computeSharedKeyBytes(bobPublicKey, alicePrivateKey)

        val iv = SecureRandom.nextBytes(16)

        val original = "000102030405060708090A0B0C0D0E0F".hexToByteArray()
        val encrypted = AES.encryptAesCtr(original, sharedKey, iv, CipherPadding.PKCS7Padding)
        val decrypted = AES.decryptAesCtr(encrypted, sharedKey, iv, CipherPadding.PKCS7Padding)

        assertTrue { decrypted.contentEquals(original) }
    }
}