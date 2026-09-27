package com.evolet.tachyon.handshake

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.FileProvider
import com.evolet.tachyon.data.Commitment
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidKeystoreHandshakeSigner(
    private val alias: String = KEY_ALIAS,
) : HandshakeSigner {
    private val keyStore: KeyStore
        get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override val publicKeyBase64: String
        get() {
            ensureKey()
            return Base64.getEncoder().encodeToString(requireNotNull(keyStore.getCertificate(alias)).publicKey.encoded)
        }

    override fun sign(bytes: ByteArray): String {
        ensureKey()
        val privateKey = requireNotNull(keyStore.getKey(alias, null))
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey as java.security.PrivateKey)
            update(bytes)
            sign()
        }
        return Base64.getEncoder().encodeToString(signature)
    }

    fun delete() {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun ensureKey() {
        if (keyStore.containsAlias(alias)) return
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
            initialize(spec)
            generateKeyPair()
        }
    }

    companion object {
        const val KEY_ALIAS = "eraya.handshake.owner.v1"
    }
}

class HandshakeLedgerStore(context: Context) {
    private val directory = File(context.filesDir, "twin").apply { mkdirs() }
    private val file = File(directory, "handshake_ledger.jsonl")
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true; explicitNulls = true }

    @Synchronized
    fun read(): List<LedgerEntry> {
        if (!file.exists()) return emptyList()
        return file.readLines(Charsets.UTF_8)
            .filter(String::isNotBlank)
            .map { json.decodeFromString<LedgerEntry>(it) }
    }

    @Synchronized
    fun append(packet: HandshakePacket): LedgerEntry {
        val current = read()
        val commitmentHash = HandshakeProtocol.commitmentHash(packet.commitment)
        current.firstOrNull { it.commitmentHash == commitmentHash }?.let { return it }
        val entry = HandshakeLedger.append(current, packet)
        file.appendText(json.encodeToString(entry) + "\n", Charsets.UTF_8)
        return entry
    }

    @Synchronized
    fun wipe(): Int {
        val count = read().size
        if (file.exists()) file.delete()
        return count
    }
}

object HandshakeQr {
    fun render(wire: String, size: Int = 900): Bitmap {
        val matrix = QRCodeWriter().encode(
            wire,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
            ),
        )
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }
}

private class OfflineQrScanner(private val context: Context) {
    suspend fun scan(uri: Uri): String = suspendCancellableCoroutine { continuation ->
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse {
            scanner.close()
            continuation.resumeWithException(it)
            return@suspendCancellableCoroutine
        }
        val task = scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull(Barcode::getRawValue)
                if (continuation.isActive) {
                    if (value == null) continuation.resumeWithException(IllegalArgumentException("No QR code was found in the photo"))
                    else continuation.resume(value)
                }
            }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            .addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
        task.addOnCompleteListener { scanner.close() }
        continuation.invokeOnCancellation { scanner.close() }
    }
}

data class HandshakeUi(
    val outboundWire: String? = null,
    val incoming: HandshakePacket? = null,
    val ledger: List<LedgerEntry> = emptyList(),
    val chain: LedgerVerification = LedgerVerification(true),
    val busy: Boolean = false,
    val status: String = "Create an offer or scan another ERAYA phone.",
    val error: String? = null,
    val tamperDemo: Boolean = false,
)

class HandshakeController(
    context: Context,
    private val scope: CoroutineScope,
    private val signer: AndroidKeystoreHandshakeSigner = AndroidKeystoreHandshakeSigner(),
    private val store: HandshakeLedgerStore = HandshakeLedgerStore(context),
) {
    private val appContext = context.applicationContext
    private val scanner = OfflineQrScanner(appContext)
    private val captureDir = File(appContext.filesDir, "handshake").apply { mkdirs() }
    private val mutable = MutableStateFlow(loadUi())
    val ui: StateFlow<HandshakeUi> = mutable.asStateFlow()

    fun createOffer(commitment: Commitment) {
        if (mutable.value.busy) return
        scope.launch {
            mutable.update { it.copy(busy = true, error = null, tamperDemo = false) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val packet = HandshakeProtocol.offer(PublicCommitment.from(commitment), signer)
                    packet to HandshakeProtocol.encode(packet)
                }
            }.onSuccess { (packet, wire) ->
                mutable.update {
                    it.copy(
                        outboundWire = wire,
                        incoming = packet,
                        busy = false,
                        status = "Signed offer ready. Let the second ERAYA phone scan this QR.",
                    )
                }
            }.onFailure(::fail)
        }
    }

    fun scanPhoto(uri: Uri) {
        if (mutable.value.busy) return
        scope.launch {
            mutable.update { it.copy(busy = true, error = null, status = "Checking QR and signatures on device…") }
            try {
                val packet = withContext(Dispatchers.IO) {
                    HandshakeProtocol.decode(scanner.scan(uri)).getOrThrow()
                }
                when (packet.kind) {
                    HANDSHAKE_OFFER -> mutable.update {
                        it.copy(
                            incoming = packet,
                            outboundWire = null,
                            busy = false,
                            status = "Owner signature verified. Review the commitment, then countersign.",
                        )
                    }
                    HANDSHAKE_COUNTERSIGN -> finishOwnerHandshake(packet)
                    else -> error("Unknown handshake packet")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun countersignIncoming() {
        val offer = mutable.value.incoming ?: return
        if (offer.kind != HANDSHAKE_OFFER || mutable.value.busy) return
        scope.launch {
            mutable.update { it.copy(busy = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val packet = HandshakeProtocol.countersign(offer, signer)
                    store.append(packet)
                    Triple(packet, HandshakeProtocol.encode(packet), store.read())
                }
            }.onSuccess { (packet, wire, entries) ->
                mutable.update {
                    it.copy(
                        incoming = packet,
                        outboundWire = wire,
                        ledger = entries,
                        chain = HandshakeLedger.verify(entries),
                        busy = false,
                        status = "Signed by both on this phone. Let the owner scan the return QR.",
                    )
                }
            }.onFailure(::fail)
        }
    }

    fun newScanCapture(): Pair<File, Uri> {
        val file = File(captureDir, "incoming-${System.currentTimeMillis()}.jpg").apply { createNewFile() }
        return file to FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
    }

    fun toggleTamperDemo() {
        mutable.update { current ->
            val enabled = !current.tamperDemo
            val checked = if (enabled && current.ledger.isNotEmpty()) {
                val changed = current.ledger.toMutableList()
                changed[0] = changed[0].copy(packetWire = changed[0].packetWire + "x")
                HandshakeLedger.verify(changed)
            } else {
                HandshakeLedger.verify(current.ledger)
            }
            current.copy(
                tamperDemo = enabled,
                chain = checked,
                status = if (enabled) "Tamper simulation changed one byte in memory; the chain rejected it." else "Stored ledger restored and verified.",
            )
        }
    }

    fun wipe(): Int {
        val count = store.wipe()
        signer.delete()
        captureDir.listFiles()?.forEach(File::delete)
        mutable.value = HandshakeUi(status = "Handshake identity and ledger deleted.")
        return count
    }

    private suspend fun finishOwnerHandshake(packet: HandshakePacket) {
        val result = withContext(Dispatchers.IO) {
            require(packet.ownerPublicKey == signer.publicKeyBase64) { "This return QR belongs to a different owner phone" }
            store.append(packet)
            store.read()
        }
        mutable.update {
            it.copy(
                incoming = packet,
                outboundWire = null,
                ledger = result,
                chain = HandshakeLedger.verify(result),
                busy = false,
                status = "Signed by both. The commitment is now in the verified local ledger.",
            )
        }
    }

    private fun loadUi(): HandshakeUi = runCatching {
        val entries = store.read()
        HandshakeUi(ledger = entries, chain = HandshakeLedger.verify(entries))
    }.getOrElse { HandshakeUi(error = "Could not open ledger: ${it.message}") }

    private fun fail(error: Throwable) {
        mutable.update {
            it.copy(
                busy = false,
                error = error.message ?: "Handshake failed",
                status = "Nothing was added to the ledger.",
            )
        }
    }
}
