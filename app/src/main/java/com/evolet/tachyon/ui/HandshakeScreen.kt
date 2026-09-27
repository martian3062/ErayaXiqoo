package com.evolet.tachyon.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.handshake.HANDSHAKE_COUNTERSIGN
import com.evolet.tachyon.handshake.HANDSHAKE_OFFER
import com.evolet.tachyon.handshake.HandshakeQr
import com.evolet.tachyon.handshake.HandshakeUi
import com.evolet.tachyon.ui.components.GlassCard
import com.evolet.tachyon.ui.components.PageIntro
import com.evolet.tachyon.ui.components.StatusPill
import com.evolet.tachyon.ui.components.TachyonIcons
import java.io.File

@Composable
fun HandshakeScreen(
    accepted: List<Commitment>,
    ui: HandshakeUi,
    onCreateOffer: (Commitment) -> Unit,
    onNewScanCapture: () -> Pair<File, Uri>,
    onScanPhoto: (Uri) -> Unit,
    onCountersign: () -> Unit,
    onToggleTamper: () -> Unit,
) {
    var selectedId by remember(accepted) { mutableStateOf(accepted.firstOrNull()?.id) }
    var pendingCapture by remember { mutableStateOf<Pair<File, Uri>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val captured = pendingCapture
        if (saved && captured != null) onScanPhoto(captured.second)
        else captured?.first?.delete()
        pendingCapture = null
    }
    val qr = remember(ui.outboundWire) {
        ui.outboundWire?.let { runCatching { HandshakeQr.render(it) }.getOrNull() }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageIntro(
                eyebrow = "Two-phone trust",
                title = "Signed handshake",
                supporting = "Both phones verify the same public commitment. No account, cloud or shared secret.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(TachyonIcons.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Propose → verify → countersign", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "The QR contains only owner, task, recipient and deadline. Persona, evidence, voice data and private people metadata never enter it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusPill(if (ui.chain.valid) "LEDGER VERIFIED" else "LEDGER TAMPERED", active = ui.chain.valid)
                    Text(ui.status, style = MaterialTheme.typography.bodyMedium)
                    if (ui.error != null) Text(ui.error, color = MaterialTheme.colorScheme.error)
                    if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }

        if (accepted.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Accept a commitment first, then return here to sign it.", modifier = Modifier.padding(16.dp))
                }
            }
        } else if (ui.outboundWire == null && ui.incoming?.kind != HANDSHAKE_OFFER) {
            item { Text("Choose a confirmed commitment", style = MaterialTheme.typography.titleMedium) }
            items(accepted.take(6), key = Commitment::id) { commitment ->
                Surface(
                    onClick = { selectedId = commitment.id },
                    color = if (selectedId == commitment.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(commitment.task, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(commitment.toWhom?.let { "For $it" }, commitment.deadlineText.takeIf(String::isNotBlank)?.let { "Due $it" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = { accepted.firstOrNull { it.id == selectedId }?.let(onCreateOffer) },
                    enabled = selectedId != null && !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create signed offer") }
            }
        }

        if (qr != null) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (ui.incoming?.kind == HANDSHAKE_COUNTERSIGN) "Return signature QR" else "Owner offer QR",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "ERAYA signed commitment QR",
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                        Text(
                            if (ui.incoming?.kind == HANDSHAKE_COUNTERSIGN) "The owner scans this back to complete both ledgers." else "The second ERAYA phone scans and verifies this before signing.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        ui.incoming?.let { packet ->
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (packet.kind == HANDSHAKE_OFFER) "Verified incoming offer" else "Dual-signed commitment",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(packet.commitment.task, style = MaterialTheme.typography.titleLarge)
                        Text(
                            listOfNotNull(
                                packet.commitment.owner.takeIf(String::isNotBlank)?.let { "Owner $it" },
                                packet.commitment.toWhom?.let { "For $it" },
                                packet.commitment.deadlineText.takeIf(String::isNotBlank)?.let { "Due $it" },
                            ).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (packet.kind == HANDSHAKE_OFFER) {
                            Button(onClick = onCountersign, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                                Icon(TachyonIcons.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" Review and countersign")
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    val target = onNewScanCapture()
                    pendingCapture = target
                    camera.launch(target.second)
                },
                enabled = !ui.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(TachyonIcons.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Photograph another phone's QR")
            }
        }

        if (ui.ledger.isNotEmpty()) {
            item {
                Text("Verified local ledger", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${ui.ledger.size} dual-signed commitment${if (ui.ledger.size == 1) "" else "s"} · hash-chained on this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ui.ledger.asReversed().take(5), key = { it.entryHash }) { entry ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ENTRY ${entry.seq} · SIGNED BY BOTH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("Commitment #${entry.commitmentHash.take(12)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Chain #${entry.entryHash.take(12)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                OutlinedButton(onClick = onToggleTamper, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ui.tamperDemo) "Restore verified ledger" else "Demo one-byte tamper detection")
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
