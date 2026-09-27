package com.evolet.tachyon.twin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One guided camera view used by the private Replica Studio capture set. */
data class ReplicaPose(
    val id: String,
    val label: String,
    val instruction: String,
)

object ReplicaCapturePlan {
    val poses = listOf(
        ReplicaPose("front", "Front", "Look straight at the lens with a relaxed expression"),
        ReplicaPose("left_15", "Left · 15°", "Turn slightly left while keeping your eyes on the lens"),
        ReplicaPose("left_45", "Left · 45°", "Turn halfway toward your left shoulder"),
        ReplicaPose("left_profile", "Left profile", "Show a clean left-side profile"),
        ReplicaPose("right_15", "Right · 15°", "Turn slightly right while keeping your eyes on the lens"),
        ReplicaPose("right_45", "Right · 45°", "Turn halfway toward your right shoulder"),
        ReplicaPose("right_profile", "Right profile", "Show a clean right-side profile"),
        ReplicaPose("chin_up", "Chin up", "Lift your chin slightly; keep your full face in frame"),
        ReplicaPose("chin_down", "Chin down", "Lower your chin slightly without looking away"),
        ReplicaPose("expression", "Natural smile", "Use the expression that feels most like you"),
    )
}

data class ReplicaSnapshot(
    val capturedPoseIds: Set<String> = emptySet(),
    val hasMotionVideo: Boolean = false,
    val generatedAt: Long? = null,
    val meshReady: Boolean = false,
    val portraitReady: Boolean = false,
    val vertexCount: Int = 0,
    val triangleCount: Int = 0,
    val style: String? = null,
    val reconstructionScope: String? = null,
    val recommendedRotationDegrees: Int = 30,
    val consented: Boolean = false,
) {
    val capturedCount: Int get() = capturedPoseIds.size
    val captureComplete: Boolean
        get() = capturedPoseIds.containsAll(ReplicaCapturePlan.poses.map { it.id }) && hasMotionVideo
}

/**
 * Private file store for Replica Studio. Media never enters MediaStore, cloud backup or an export.
 * Capture is performed by the device camera through one-shot content URIs.
 */
class ReplicaStore(private val context: Context) {
    private val directory = File(context.filesDir, "replica").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("replica_state", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(readSnapshot())

    val state: StateFlow<ReplicaSnapshot> = mutable.asStateFlow()

    fun setConsent(consented: Boolean) {
        preferences.edit { putBoolean(KEY_CONSENT, consented) }
        refresh()
    }

    fun photoFile(pose: ReplicaPose): File = File(directory, "${pose.id}.jpg")

    fun motionFile(): File = File(directory, "motion.mp4")

    fun meshFile(): File = File(directory, MESH)

    fun meshObjFile(): File = File(directory, OBJ)

    fun meshMaterialFile(): File = File(directory, MATERIAL)

    fun meshTextureFile(): File = File(directory, TEXTURE)

    fun portraitFile(): File = File(directory, PORTRAIT)

    fun talkingPortraitFile(): File = File(directory, TALKING_PORTRAIT)

    fun blinkPortraitFile(): File = File(directory, BLINK_PORTRAIT)

    fun lookLeftPortraitFile(): File = File(directory, LOOK_LEFT_PORTRAIT)

    fun lookRightPortraitFile(): File = File(directory, LOOK_RIGHT_PORTRAIT)

    fun meshExportFiles(): List<File> = listOf(meshObjFile(), meshMaterialFile(), meshTextureFile()).filter(File::isFile)

    fun newPendingPhoto(pose: ReplicaPose): File =
        File(directory, ".pending-${pose.id}-${System.currentTimeMillis()}.jpg").apply { createNewFile() }

    fun newPendingVideo(): File =
        File(directory, ".pending-motion-${System.currentTimeMillis()}.mp4").apply { createNewFile() }

    fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun commitPhoto(pose: ReplicaPose, pending: File) {
        commit(pending, photoFile(pose))
    }

    fun commitVideo(pending: File) {
        commit(pending, motionFile())
    }

    fun discard(pending: File?) {
        pending?.takeIf { it.name.startsWith(".pending-") }?.delete()
        refresh()
    }

    internal fun saveGenerated(mesh: ReplicaMesh, texture: Bitmap): ReplicaSnapshot {
        val before = readSnapshot()
        require(before.captureComplete) { "Capture all ten views and one motion video first" }
        require(before.consented) { "Consent is required before generating a replica" }

        File(directory, MANIFEST).delete()
        val meshTemp = File(directory, "$MESH.tmp")
        val objTemp = File(directory, "$OBJ.tmp")
        val materialTemp = File(directory, "$MATERIAL.tmp")
        val textureTemp = File(directory, "$TEXTURE.tmp")
        ReplicaMeshCodec.writeBinary(meshTemp, mesh)
        ReplicaMeshCodec.writeObj(objTemp, materialTemp, TEXTURE, mesh)
        textureTemp.outputStream().buffered().use { out ->
            require(texture.compress(Bitmap.CompressFormat.JPEG, 92, out)) { "Could not encode replica texture" }
        }
        replace(meshTemp, meshFile())
        replace(objTemp, meshObjFile())
        replace(materialTemp, meshMaterialFile())
        replace(textureTemp, meshTextureFile())
        meshTextureFile().copyTo(portraitFile(), overwrite = true)
        portraitFile().copyTo(talkingPortraitFile(), overwrite = true)
        portraitFile().copyTo(blinkPortraitFile(), overwrite = true)
        portraitFile().copyTo(lookLeftPortraitFile(), overwrite = true)
        portraitFile().copyTo(lookRightPortraitFile(), overwrite = true)

        val generatedAt = System.currentTimeMillis()
        val manifest = JSONObject()
            .put("schema", "eraya.replica.mesh.v1")
            .put("generated_at", generatedAt)
            .put("device_only", true)
            .put("engine", "mlkit-face-mesh-468")
            .put("reconstruction_scope", "textured_front_face_relief")
            .put("recommended_rotation_degrees", 30)
            .put("vertex_count", mesh.vertexCount)
            .put("triangle_count", mesh.triangleCount)
            .put("mesh", MESH)
            .put("blender_obj", OBJ)
            .put("material", MATERIAL)
            .put("texture", TEXTURE)
            .put("poses", JSONArray(ReplicaCapturePlan.poses.map { it.id }))
            .put("motion_video", motionFile().name)
        val manifestTemp = File(directory, "$MANIFEST.tmp").apply { writeText(manifest.toString(2)) }
        replace(manifestTemp, File(directory, MANIFEST))
        refresh()
        return mutable.value
    }

    /** Imports a laptop-generated ERAYA package after validating every file and mesh index. */
    fun importGenerated(uri: Uri): ReplicaSnapshot {
        require(state.value.consented) { "Consent is required before importing a replica" }
        val input = context.contentResolver.openInputStream(uri)
            ?: error("The selected replica package could not be opened")
        return input.use(::importGenerated)
    }

    /**
     * Consumes a package placed in this app's private external-files inbox.
     *
     * This is intentionally a fixed app-scoped path and filename. It gives the owner a reliable
     * laptop-to-phone transfer route without broad storage permission or an exported receiver.
     * The ZIP is deleted after a validated import; a small result receipt contains no personal data.
     */
    fun importPrivateInbox(): Result<ReplicaSnapshot>? {
        val inbox = context.getExternalFilesDir(INBOX_DIRECTORY) ?: return null
        val packageFile = File(inbox, INBOX_PACKAGE)
        if (!packageFile.isFile) return null
        val receipt = File(inbox, INBOX_RECEIPT)
        return runCatching {
            packageFile.inputStream().buffered().use(::importGenerated)
        }.onSuccess { snapshot ->
            receipt.writeText("ok vertices=${snapshot.vertexCount} triangles=${snapshot.triangleCount} style=${snapshot.style.orEmpty()}\n")
            packageFile.delete()
            Log.i(TAG, "Imported replica from private inbox: ${snapshot.vertexCount} vertices")
        }.onFailure { error ->
            receipt.writeText("error ${error.message ?: error.javaClass.simpleName}\n")
            Log.w(TAG, "Private inbox replica import failed", error)
        }
    }

    internal fun importGenerated(input: InputStream): ReplicaSnapshot {
        require(state.value.consented) { "Consent is required before importing a replica" }
        val temp = File(directory, ".import-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val required = linkedMapOf(
                MESH to File(temp, MESH),
                OBJ to File(temp, OBJ),
                MATERIAL to File(temp, MATERIAL),
                TEXTURE to File(temp, TEXTURE),
                MANIFEST to File(temp, MANIFEST),
            )
            val expected = LinkedHashMap(required).apply {
                put(PORTRAIT, File(temp, PORTRAIT))
                put(TALKING_PORTRAIT, File(temp, TALKING_PORTRAIT))
                put(BLINK_PORTRAIT, File(temp, BLINK_PORTRAIT))
                put(LOOK_LEFT_PORTRAIT, File(temp, LOOK_LEFT_PORTRAIT))
                put(LOOK_RIGHT_PORTRAIT, File(temp, LOOK_RIGHT_PORTRAIT))
            }
            val seen = linkedSetOf<String>()
            var totalBytes = 0L
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.replace('\\', '/')
                    require('/' !in name && name in expected) { "Unexpected file in replica package: $name" }
                    require(seen.add(name)) { "Duplicate file in replica package: $name" }
                    val limit = IMPORT_LIMITS.getValue(name)
                    var fileBytes = 0L
                    expected.getValue(name).outputStream().buffered().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            fileBytes += read
                            totalBytes += read
                            require(fileBytes <= limit && totalBytes <= MAX_IMPORT_BYTES) { "Replica package is too large" }
                            out.write(buffer, 0, read)
                        }
                    }
                }
            }
            require(seen.containsAll(required.keys)) { "Replica package is incomplete" }

            val mesh = ReplicaMeshCodec.readBinary(expected.getValue(MESH))
            val manifest = JSONObject(expected.getValue(MANIFEST).readText())
            require(manifest.optString("schema") == "eraya.replica.mesh.v1") { "Unsupported replica package" }
            require(manifest.optInt("vertex_count") == mesh.vertexCount) { "Replica vertex count does not match" }
            require(manifest.optInt("triangle_count") == mesh.triangleCount) { "Replica triangle count does not match" }
            require(expected.getValue(OBJ).readText().contains("o ErayaReplicaFace")) { "Blender mesh is invalid" }
            require(expected.getValue(MATERIAL).readText().contains("map_Kd $TEXTURE")) { "Replica material is invalid" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(expected.getValue(TEXTURE).path, bounds)
            require(bounds.outWidth in 64..4_096 && bounds.outHeight in 64..4_096) { "Replica texture is invalid" }
            expected.getValue(PORTRAIT).takeIf(File::isFile)?.let { portrait ->
                val portraitBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(portrait.path, portraitBounds)
                require(portraitBounds.outWidth in 320..4_096 && portraitBounds.outHeight in 240..4_096) {
                    "Replica portrait is invalid"
                }
            }
            for (name in listOf(TALKING_PORTRAIT, BLINK_PORTRAIT, LOOK_LEFT_PORTRAIT, LOOK_RIGHT_PORTRAIT)) {
                expected.getValue(name).takeIf(File::isFile)?.let { frame ->
                    val frameBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(frame.path, frameBounds)
                    require(frameBounds.outWidth in 320..4_096 && frameBounds.outHeight in 240..4_096) {
                        "Replica animation frame is invalid"
                    }
                }
            }

            manifest
                .put("generated_at", System.currentTimeMillis())
                .put("device_only", true)
                .put("source", "laptop_import")
            expected.getValue(MANIFEST).writeText(manifest.toString(2))

            // The manifest is installed last, so a partial copy is never presented as ready.
            File(directory, MANIFEST).delete()
            portraitFile().delete()
            talkingPortraitFile().delete()
            blinkPortraitFile().delete()
            lookLeftPortraitFile().delete()
            lookRightPortraitFile().delete()
            for (name in listOf(MESH, OBJ, MATERIAL, TEXTURE)) replace(expected.getValue(name), File(directory, name))
            expected.getValue(PORTRAIT).takeIf(File::isFile)?.let { replace(it, portraitFile()) }
            expected.getValue(TALKING_PORTRAIT).takeIf(File::isFile)?.let { replace(it, talkingPortraitFile()) }
            expected.getValue(BLINK_PORTRAIT).takeIf(File::isFile)?.let { replace(it, blinkPortraitFile()) }
            expected.getValue(LOOK_LEFT_PORTRAIT).takeIf(File::isFile)?.let { replace(it, lookLeftPortraitFile()) }
            expected.getValue(LOOK_RIGHT_PORTRAIT).takeIf(File::isFile)?.let { replace(it, lookRightPortraitFile()) }
            replace(expected.getValue(MANIFEST), File(directory, MANIFEST))
            refresh()
            return mutable.value
        } finally {
            temp.deleteRecursively()
        }
    }

    fun loadMesh(): ReplicaMesh = ReplicaMeshCodec.readBinary(meshFile())

    fun wipe(): Int {
        val count = directory.listFiles()?.size ?: 0
        directory.deleteRecursively()
        directory.mkdirs()
        preferences.edit { clear() }
        refresh()
        return count
    }

    fun refresh() {
        mutable.value = readSnapshot()
    }

    private fun commit(pending: File, target: File) {
        require(pending.parentFile?.canonicalFile == directory.canonicalFile) { "Capture outside replica directory" }
        require(pending.exists() && pending.length() > 0L) { "Camera returned an empty capture" }
        if (target.exists()) target.delete()
        if (!pending.renameTo(target)) {
            pending.copyTo(target, overwrite = true)
            pending.delete()
        }
        invalidateGenerated()
        refresh()
    }

    private fun invalidateGenerated() {
        listOf(MANIFEST, MESH, OBJ, MATERIAL, TEXTURE, PORTRAIT, TALKING_PORTRAIT, BLINK_PORTRAIT, LOOK_LEFT_PORTRAIT, LOOK_RIGHT_PORTRAIT).forEach {
            File(directory, it).delete()
        }
    }

    private fun replace(source: File, target: File) {
        if (target.exists()) target.delete()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    private fun readSnapshot(): ReplicaSnapshot {
        val captured = ReplicaCapturePlan.poses
            .filter { photoFile(it).let { file -> file.exists() && file.length() > 0L } }
            .mapTo(linkedSetOf()) { it.id }
        val video = motionFile().let { it.exists() && it.length() > 0L }
        val manifest = runCatching {
            File(directory, MANIFEST).takeIf(File::exists)?.readText()?.let(::JSONObject)
        }.getOrNull()
        val meshReady = manifest != null && meshFile().isFile && meshObjFile().isFile &&
            meshMaterialFile().isFile && meshTextureFile().isFile
        val portraitReady = portraitFile().isFile
        return ReplicaSnapshot(
            capturedPoseIds = captured,
            hasMotionVideo = video,
            generatedAt = manifest?.takeIf { meshReady }?.optLong("generated_at")?.takeIf { it > 0L },
            meshReady = meshReady,
            portraitReady = portraitReady,
            vertexCount = manifest?.takeIf { meshReady }?.optInt("vertex_count") ?: 0,
            triangleCount = manifest?.takeIf { meshReady }?.optInt("triangle_count") ?: 0,
            style = manifest?.takeIf { meshReady }?.optString("style")?.takeIf(String::isNotBlank),
            reconstructionScope = manifest?.takeIf { meshReady }?.optString("reconstruction_scope")?.takeIf(String::isNotBlank),
            recommendedRotationDegrees = manifest?.takeIf { meshReady }?.optInt("recommended_rotation_degrees", 30) ?: 30,
            consented = preferences.getBoolean(KEY_CONSENT, false),
        )
    }

    companion object {
        private const val TAG = "ReplicaStore"
        private const val KEY_CONSENT = "consent"
        private const val INBOX_DIRECTORY = "replica_import"
        private const val INBOX_PACKAGE = "ERAYA-anime-avatar.zip"
        private const val INBOX_RECEIPT = "import-result.txt"
        private const val MANIFEST = "replica.json"
        private const val MESH = "replica.mesh"
        private const val OBJ = "replica.obj"
        private const val MATERIAL = "replica.mtl"
        private const val TEXTURE = "replica_texture.jpg"
        private const val PORTRAIT = "replica_portrait.jpg"
        private const val TALKING_PORTRAIT = "replica_talking.jpg"
        private const val BLINK_PORTRAIT = "replica_blink.jpg"
        private const val LOOK_LEFT_PORTRAIT = "replica_look_left.jpg"
        private const val LOOK_RIGHT_PORTRAIT = "replica_look_right.jpg"
        private const val MAX_IMPORT_BYTES = 32L * 1024 * 1024
        private val IMPORT_LIMITS = mapOf(
            MESH to 2L * 1024 * 1024,
            OBJ to 12L * 1024 * 1024,
            MATERIAL to 64L * 1024,
            TEXTURE to 16L * 1024 * 1024,
            PORTRAIT to 16L * 1024 * 1024,
            TALKING_PORTRAIT to 16L * 1024 * 1024,
            BLINK_PORTRAIT to 16L * 1024 * 1024,
            LOOK_LEFT_PORTRAIT to 16L * 1024 * 1024,
            LOOK_RIGHT_PORTRAIT to 16L * 1024 * 1024,
            MANIFEST to 64L * 1024,
        )
    }
}
