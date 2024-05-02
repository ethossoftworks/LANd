package com.ethossoftworks.land.lib.crypto

import kotlin.test.Test
import kotlin.test.assertTrue

class DHKeyTest {
    @Test
    fun dhKey() {
        // Compute Private Keys
        val alicePrivateKey = DHKey.generatePrivateKey()
        val bobPrivateKey = DHKey.generatePrivateKey()

        assertTrue { alicePrivateKey != bobPrivateKey }

        // Compute Public Keys
        val alicePublicKey = DHKey.computePublicKey(alicePrivateKey)
        val bobPublicKey = DHKey.computePublicKey(bobPrivateKey)

        assertTrue { alicePublicKey != bobPublicKey }

        // Compute shared secrets
        val aliceSharedSecret = DHKey.computeSharedKeyBytes(bobPublicKey, alicePrivateKey)
        val bobSharedSecret = DHKey.computeSharedKeyBytes(alicePublicKey, bobPrivateKey)

        assertTrue { aliceSharedSecret.contentEquals(bobSharedSecret) }
    }
}