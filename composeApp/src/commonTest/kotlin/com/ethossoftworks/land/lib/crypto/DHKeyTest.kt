package com.ethossoftworks.land.lib.crypto

import kotlin.test.Test
import kotlin.test.assertTrue

class DHKeyTest {
    @Test
    fun dhKey() {
        // Compute Private Keys
        val alicePrivateKey = DHKey.generatePrivateKey(2_048)
        val bobPrivateKey = DHKey.generatePrivateKey(2_048)

        assertTrue { alicePrivateKey != bobPrivateKey }

        // Compute Public Keys
        val alicePublicKey = DHKey.computePublicKey(alicePrivateKey)
        val bobPublicKey = DHKey.computePublicKey(bobPrivateKey)

        assertTrue { alicePublicKey != bobPublicKey }

        // Compute shared secrets
        val aliceSharedSecret = DHKey.computeSharedSecret(bobPublicKey, alicePrivateKey)
        val bobSharedSecret = DHKey.computeSharedSecret(alicePublicKey, bobPrivateKey)

        assertTrue { aliceSharedSecret.contentEquals(bobSharedSecret) }
    }
}