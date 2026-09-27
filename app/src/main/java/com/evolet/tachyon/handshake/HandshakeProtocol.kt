package com.evolet.tachyon.handshake

import com.evolet.tachyon.data.Commitment
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

const val HANDSHAKE_VERSION = 1
const val HANDSHAKE_OFFER = "offer"
const val HANDSHAKE_COUNTERSIGN = "countersign"
private const val WIRE_PREFIX = "ERAYA1:"

/** The only commitment fields allowed to leave the app in a handshake QR. */
@Serializable
data class PublicCommitment(
    val id: String,
    val owner: String,
    val task: String,
    val toWhom: String? = null,
    val deadlineText: String,
    val deadlineIso: String? = null,
) {
    companion object {
        fun from(commitment: Commitment) = PublicCommitment(
            id = clean(commitment.id, 96),
            owner = clean(commitment.owner, 80),
            task = clean(commitment.task, 280),
            toWhom = commitment.toWhom?.let { clean(it, 80) }?.takeIf(String::isNotBlank),
            deadlineText = clean(commitment.deadlineText, 120),
            deadlineIso = commitment.deadlineIso?.let { clean(it, 48) }?.takeIf(String::isNotBlank),
        )

        private fun clean(value: String, limit: Int): String =
            value.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(limit)
    }
}

@Serializable
data class HandshakePacket(
    val version: Int = HANDSHAKE_VERSION,
    val kind: String,
    val commitment: PublicCommitment,
    val offeredAt: Long,
    val ownerPublicKey: String,
    val ownerSignature: String,
    val counterSignedAt: Long? = null,
    val counterpartyPublicKey: String? = null,
    val counterpartySignature: String? = null,
)

interface HandshakeSigner {
    val publicKeyBase64: String
    fun sign(bytes: ByteArray): String
}

data class PacketVerification(val valid: Boolean, val reason: String)

object HandshakeProtocol {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun offer(
        commitment: PublicCommitment,
        signer: HandshakeSigner,
        offeredAt: Long = System.currentTimeMillis(),
    ): HandshakePacket {
        require(commitment.id.isNotBlank() && commitment.owner.isNotBlank() && commitment.task.isNotBlank())
        val unsigned = HandshakePacket(
            kind = HANDSHAKE_OFFER,
            commitment = commitment,
            offeredAt = offeredAt,
            ownerPublicKey = signer.publicKeyBase64,
            ownerSignature = "",
        )
        return unsigned.copy(ownerSignature = signer.sign(offerBytes(unsigned)))
    }

    fun countersign(
        offer: HandshakePacket,
        signer: HandshakeSigner,
        signedAt: Long = System.currentTimeMillis(),
    ): HandshakePacket {
        require(verify(offer).valid) { "Owner signature is invalid" }
        require(offer.kind == HANDSHAKE_OFFER) { "Only an offer can be countersigned" }
        require(signer.publicKeyBase64 != offer.ownerPublicKey) { "A second device is required to countersign" }
        val unsigned = offer.copy(
            kind = HANDSHAKE_COUNTERSIGN,
            counterSignedAt = signedAt,
            counterpartyPublicKey = signer.publicKeyBase64,
            counterpartySignature = "",
        )
        return unsigned.copy(counterpartySignature = signer.sign(counterBytes(unsigned)))
    }

    fun verify(packet: HandshakePacket): PacketVerification = runCatching {
        require(packet.version == HANDSHAKE_VERSION) { "Unsupported handshake version" }
        require(packet.commitment.id.isNotBlank()) { "Missing commitment id" }
        require(packet.commitment.owner.isNotBlank()) { "Missing owner" }
        require(packet.commitment.task.isNotBlank()) { "Missing task" }
        require(packet.offeredAt > 0L) { "Missing offer time" }
        require(verifySignature(packet.ownerPublicKey, offerBytes(packet), packet.ownerSignature)) {
            "Owner signature does not match the commitment"
        }
        when (packet.kind) {
            HANDSHAKE_OFFER -> {
                require(packet.counterSignedAt == null && packet.counterpartyPublicKey == null && packet.counterpartySignature == null) {
                    "Offer contains unexpected countersignature data"
                }
            }
            HANDSHAKE_COUNTERSIGN -> {
                require(packet.counterSignedAt != null && packet.counterSignedAt >= packet.offeredAt) { "Invalid countersign time" }
                val counterKey = requireNotNull(packet.counterpartyPublicKey) { "Missing counterparty key" }
                val counterSignature = requireNotNull(packet.counterpartySignature) { "Missing counterparty signature" }
                require(counterKey != packet.ownerPublicKey) { "Owner cannot countersign their own offer" }
                require(verifySignature(counterKey, counterBytes(packet), counterSignature)) {
                    "Counterparty signature does not match the commitment"
                }
            }
            else -> error("Unknown handshake packet type")
        }
        PacketVerification(true, "Verified")
    }.getOrElse { PacketVerification(false, it.message ?: "Invalid handshake") }

    fun encode(packet: HandshakePacket): String {
        require(verify(packet).valid) { verify(packet).reason }
        return WIRE_PREFIX + json.encodeToString(packet)
    }

    fun decode(wire: String): Result<HandshakePacket> = runCatching {
        require(wire.startsWith(WIRE_PREFIX)) { "This is not an ERAYA handshake QR" }
        val packet = json.decodeFromString<HandshakePacket>(wire.removePrefix(WIRE_PREFIX))
        val checked = verify(packet)
        require(checked.valid) { checked.reason }
        packet
    }

    fun commitmentHash(commitment: PublicCommitment): String = sha256Hex(
        canonical(json.encodeToJsonElement(commitment)).toByteArray(StandardCharsets.UTF_8),
    )

    internal fun offerBytes(packet: HandshakePacket): ByteArray = canonical(
        buildJsonObject {
            put("commitment", json.encodeToJsonElement(packet.commitment))
            put("kind", HANDSHAKE_OFFER)
            put("offeredAt", packet.offeredAt)
            put("ownerPublicKey", packet.ownerPublicKey)
            put("version", packet.version)
        },
    ).toByteArray(StandardCharsets.UTF_8)

    internal fun counterBytes(packet: HandshakePacket): ByteArray = canonical(
        buildJsonObject {
            put("commitment", json.encodeToJsonElement(packet.commitment))
            put("counterSignedAt", requireNotNull(packet.counterSignedAt))
            put("counterpartyPublicKey", requireNotNull(packet.counterpartyPublicKey))
            put("kind", HANDSHAKE_COUNTERSIGN)
            put("offeredAt", packet.offeredAt)
            put("ownerPublicKey", packet.ownerPublicKey)
            put("ownerSignature", packet.ownerSignature)
            put("version", packet.version)
        },
    ).toByteArray(StandardCharsets.UTF_8)

    internal fun canonical(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries.sortedBy(Map.Entry<String, JsonElement>::key)
            .joinToString(prefix = "{", postfix = "}") { (key, value) ->
                "${JsonPrimitive(key)}:${canonical(value)}"
            }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]") { canonical(it) }
        else -> element.toString()
    }

    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun verifySignature(publicKey: String, data: ByteArray, signature: String): Boolean = runCatching {
        val keyBytes = Base64.getDecoder().decode(publicKey)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(data)
            verify(Base64.getDecoder().decode(signature))
        }
    }.getOrDefault(false)
}

@Serializable
data class LedgerEntry(
    val seq: Long,
    val commitmentHash: String,
    val packetWire: String,
    val previousHash: String,
    val entryHash: String,
    val createdAt: Long,
)

data class LedgerVerification(val valid: Boolean, val brokenAt: Long? = null, val reason: String = "Verified")

object HandshakeLedger {
    fun append(
        entries: List<LedgerEntry>,
        packet: HandshakePacket,
        createdAt: Long = System.currentTimeMillis(),
    ): LedgerEntry {
        require(packet.kind == HANDSHAKE_COUNTERSIGN) { "Only dual-signed commitments enter the ledger" }
        val verified = HandshakeProtocol.verify(packet)
        require(verified.valid) { verified.reason }
        val seq = (entries.maxOfOrNull(LedgerEntry::seq) ?: 0L) + 1L
        val previous = entries.maxByOrNull(LedgerEntry::seq)?.entryHash.orEmpty()
        val commitmentHash = HandshakeProtocol.commitmentHash(packet.commitment)
        val wire = HandshakeProtocol.encode(packet)
        return LedgerEntry(
            seq = seq,
            commitmentHash = commitmentHash,
            packetWire = wire,
            previousHash = previous,
            entryHash = entryHash(seq, commitmentHash, wire, previous, createdAt),
            createdAt = createdAt,
        )
    }

    fun verify(entries: List<LedgerEntry>): LedgerVerification {
        var previous = ""
        entries.sortedBy(LedgerEntry::seq).forEachIndexed { index, entry ->
            val expectedSeq = index + 1L
            if (entry.seq != expectedSeq) return LedgerVerification(false, entry.seq, "Sequence gap")
            if (entry.previousHash != previous) return LedgerVerification(false, entry.seq, "Previous hash mismatch")
            val packet = HandshakeProtocol.decode(entry.packetWire).getOrElse {
                return LedgerVerification(false, entry.seq, it.message ?: "Invalid signed payload")
            }
            if (packet.kind != HANDSHAKE_COUNTERSIGN) return LedgerVerification(false, entry.seq, "Entry is not dual-signed")
            if (HandshakeProtocol.commitmentHash(packet.commitment) != entry.commitmentHash) {
                return LedgerVerification(false, entry.seq, "Commitment hash mismatch")
            }
            val expectedHash = entryHash(
                entry.seq,
                entry.commitmentHash,
                entry.packetWire,
                entry.previousHash,
                entry.createdAt,
            )
            if (entry.entryHash != expectedHash) return LedgerVerification(false, entry.seq, "Entry hash mismatch")
            previous = entry.entryHash
        }
        return LedgerVerification(true)
    }

    private fun entryHash(
        seq: Long,
        commitmentHash: String,
        packetWire: String,
        previousHash: String,
        createdAt: Long,
    ): String = HandshakeProtocol.sha256Hex(
        HandshakeProtocol.canonical(
            buildJsonObject {
                put("commitmentHash", commitmentHash)
                put("createdAt", createdAt)
                put("packet", packetWire)
                put("previousHash", previousHash)
                put("seq", seq)
            },
        ).toByteArray(StandardCharsets.UTF_8),
    )
}
