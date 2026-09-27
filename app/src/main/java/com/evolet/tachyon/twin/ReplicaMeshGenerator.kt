package com.evolet.tachyon.twin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

enum class ReplicaGenerationStage(val label: String) {
    PREPARING("Preparing the front capture"),
    DETECTING("Finding 468 facial depth points"),
    BUILDING("Triangulating and texturing the mesh"),
    SAVING("Writing Blender OBJ locally"),
}

data class ReplicaGenerationResult(
    val vertexCount: Int,
    val triangleCount: Int,
)

/**
 * Creates an identity-specific 3D facial relief from the consented front capture.
 *
 * This follows the useful part of VECTRA's avatar pipeline: controlled face topology is more
 * reliable than pretending a general image-to-3D model reconstructed a complete head. ML Kit is
 * bundled in the APK and supplies 468 image-aligned 3D points plus stable triangles; no image or
 * landmark leaves the phone.
 */
class ReplicaMeshGenerator(
    @Suppress("unused") private val context: Context,
    private val store: ReplicaStore,
) {
    suspend fun generate(onStage: (ReplicaGenerationStage) -> Unit = {}): ReplicaGenerationResult {
        val snapshot = store.state.value
        require(snapshot.consented) { "Consent is required before generating a replica" }
        require(snapshot.captureComplete) { "Capture all ten views and the motion reference first" }

        val front = store.photoFile(ReplicaCapturePlan.poses.first { it.id == "front" })
        require(front.isFile && front.length() > 0L) { "The front capture is missing" }

        onStage(ReplicaGenerationStage.PREPARING)
        val bitmap = withContext(Dispatchers.IO) { decodeOriented(front) }
        try {
            onStage(ReplicaGenerationStage.DETECTING)
            val detector = FaceMeshDetection.getClient()
            val faces = try {
                detector.process(InputImage.fromBitmap(bitmap, 0)).awaitValue()
            } finally {
                detector.close()
            }
            require(faces.size == 1) {
                if (faces.isEmpty()) "No face found in the Front capture. Retake it in even light, facing the lens."
                else "More than one face was found. Retake Front with only the replica owner in frame."
            }

            onStage(ReplicaGenerationStage.BUILDING)
            val mesh = withContext(Dispatchers.Default) { buildMesh(faces.single(), bitmap.width, bitmap.height) }

            onStage(ReplicaGenerationStage.SAVING)
            withContext(Dispatchers.IO) { store.saveGenerated(mesh, bitmap) }
            return ReplicaGenerationResult(mesh.vertexCount, mesh.triangleCount)
        } finally {
            bitmap.recycle()
        }
    }

    private fun buildMesh(face: FaceMesh, imageWidth: Int, imageHeight: Int): ReplicaMesh {
        val points = face.allPoints
        require(points.size == EXPECTED_POINTS) { "Face detector returned ${points.size} points instead of $EXPECTED_POINTS" }

        val bounds = face.boundingBox
        val halfWidth = (bounds.width().coerceAtLeast(1) / 2f)
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val meanZ = points.sumOf { it.position.z.toDouble() }.toFloat() / points.size
        val positions = FloatArray(EXPECTED_POINTS * 3)
        val textureCoordinates = FloatArray(EXPECTED_POINTS * 2)
        val seen = BooleanArray(EXPECTED_POINTS)

        for (point in points) {
            val index = point.index
            require(index in 0 until EXPECTED_POINTS) { "Face point index $index is outside the expected topology" }
            val p = point.position
            positions[index * 3] = (p.x - centerX) / halfWidth
            positions[index * 3 + 1] = (centerY - p.y) / halfWidth
            // ML Kit uses a negative Z for points closer to the camera. Flip it so the nose
            // protrudes toward the viewer, and gently amplify the shallow selfie depth estimate.
            positions[index * 3 + 2] = -(p.z - meanZ) / halfWidth * DEPTH_GAIN
            textureCoordinates[index * 2] = (p.x / imageWidth).coerceIn(0f, 1f)
            textureCoordinates[index * 2 + 1] = (1f - p.y / imageHeight).coerceIn(0f, 1f)
            seen[index] = true
        }
        require(seen.all { it }) { "The detected face topology is incomplete" }

        val indexList = ArrayList<Int>(face.allTriangles.size * 3)
        for (triangle in face.allTriangles) {
            val p = triangle.allPoints
            if (p.size != 3) continue
            val a = p[0].index
            val b = p[1].index
            val c = p[2].index
            if (a == b || b == c || a == c) continue
            // Image Y was flipped above, so reverse winding to keep outward-facing normals.
            indexList += a
            indexList += c
            indexList += b
        }
        require(indexList.size >= 3) { "Face detector returned no mesh triangles" }
        val indices = indexList.toIntArray()
        val normals = ReplicaMeshCodec.calculateNormals(positions, indices)
        return ReplicaMesh(positions, normals, textureCoordinates, indices)
    }

    private fun decodeOriented(file: File): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val scale = (MAX_TEXTURE_EDGE.toFloat() / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize(
                (info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private suspend fun <T> Task<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Face mesh detection failed"))
        }
    }

    private companion object {
        const val EXPECTED_POINTS = 468
        const val MAX_TEXTURE_EDGE = 1_280
        const val DEPTH_GAIN = 1.35f
    }
}
