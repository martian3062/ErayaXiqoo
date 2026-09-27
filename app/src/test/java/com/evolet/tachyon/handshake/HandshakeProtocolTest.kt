package com.evolet.tachyon.handshake

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandshakeProtocolTest {
    private val owner = SoftwareSigner()
    private val counterparty = SoftwareSigner()

    @Test
    fun offerAndCountersignVerifyAcrossTwoKeys() {
        val offer = HandshakeProtocol.offer(commitment("c1"), owner, offeredAt = 100)
        val signed = HandshakeProtocol.countersign(offer, counterparty, signedAt = 200)

        assertTrue(HandshakeProtocol.verify(offer).valid)
        assertTrue(HandshakeProtocol.verify(signed).valid)
        assertEquals(HANDSHAKE_COUNTERSIGN, signed.kind)
        assertTrue(HandshakeProtocol.decode(HandshakeProtocol.encode(signed)).isSuccess)
    }

    @Test
    fun changingPublicCommitmentBreaksOwnerSignature() {
        val offer = HandshakeProtocol.offer(commitment("c1"), owner, offeredAt = 100)
        val tampered = offer.copy(commitment = offer.commitment.copy(task = "Send money instead"))

        val result = HandshakeProtocol.verify(tampered)

        assertFalse(result.valid)
        assertTrue(result.reason.contains("Owner signature"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun onePhoneCannotCountersignItsOwnOffer() {
        val offer = HandshakeProtocol.offer(commitment("c1"), owner, offeredAt = 100)
        HandshakeProtocol.countersign(offer, owner, signedAt = 200)
    }

    @Test
    fun hashChainDetectsChangedPayloadAndBrokenPreviousHash() {
        val firstPacket = HandshakeProtocol.countersign(
            HandshakeProtocol.offer(commitment("c1"), owner, offeredAt = 100),
            counterparty,
            signedAt = 200,
        )
        val first = HandshakeLedger.append(emptyList(), firstPacket, createdAt = 300)
        val secondPacket = HandshakeProtocol.countersign(
            HandshakeProtocol.offer(commitment("c2"), owner, offeredAt = 400),
            counterparty,
            signedAt = 500,
        )
        val second = HandshakeLedger.append(listOf(first), secondPacket, createdAt = 600)

        assertTrue(HandshakeLedger.verify(listOf(first, second)).valid)
        assertFalse(HandshakeLedger.verify(listOf(first.copy(packetWire = first.packetWire + "x"), second)).valid)
        assertFalse(HandshakeLedger.verify(listOf(first, second.copy(previousHash = "wrong"))).valid)
    }

    @Test
    fun publicPayloadContainsNoPrivateEvidenceOrPersonaFields() {
        val wire = HandshakeProtocol.encode(
            HandshakeProtocol.offer(commitment("c1"), owner, offeredAt = 100),
        )

        assertFalse(wire.contains("evidence", ignoreCase = true))
        assertFalse(wire.contains("persona", ignoreCase = true))
        assertFalse(wire.contains("voice", ignoreCase = true))
        assertTrue(wire.contains("Send the form"))
    }

    private fun commitment(id: String) = PublicCommitment(
        id = id,
        owner = "You",
        task = "Send the form",
        toWhom = "Alex",
        deadlineText = "Friday",
        deadlineIso = "2026-10-02T17:00:00",
    )

    private class SoftwareSigner : HandshakeSigner {
        private val pair: KeyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }

        override val publicKeyBase64: String = Base64.getEncoder().encodeToString(pair.public.encoded)

        override fun sign(bytes: ByteArray): String = Base64.getEncoder().encodeToString(
            Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private)
                update(bytes)
                sign()
            },
        )
    }
}
