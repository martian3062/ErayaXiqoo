package com.evolet.tachyon.twin

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

/** A compact, textured triangle mesh generated entirely on the phone. */
data class ReplicaMesh(
    val positions: FloatArray,
    val normals: FloatArray,
    val textureCoordinates: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    init {
        require(positions.size % 3 == 0 && positions.isNotEmpty()) { "Mesh positions are invalid" }
        require(normals.size == positions.size) { "Every vertex needs a normal" }
        require(textureCoordinates.size == vertexCount * 2) { "Every vertex needs a UV coordinate" }
        require(indices.size % 3 == 0 && indices.all { it in 0 until vertexCount }) { "Mesh indices are invalid" }
    }
}

/** Binary cache plus an OBJ/MTL export that can be opened directly in Blender. */
object ReplicaMeshCodec {
    private const val MAGIC = 0x45524159 // ERAY
    private const val VERSION = 1

    fun writeBinary(file: File, mesh: ReplicaMesh) {
        DataOutputStream(BufferedOutputStream(file.outputStream())).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(mesh.vertexCount)
            out.writeInt(mesh.indices.size)
            for (i in 0 until mesh.vertexCount) {
                out.writeFloat(mesh.positions[i * 3])
                out.writeFloat(mesh.positions[i * 3 + 1])
                out.writeFloat(mesh.positions[i * 3 + 2])
                out.writeFloat(mesh.normals[i * 3])
                out.writeFloat(mesh.normals[i * 3 + 1])
                out.writeFloat(mesh.normals[i * 3 + 2])
                out.writeFloat(mesh.textureCoordinates[i * 2])
                out.writeFloat(mesh.textureCoordinates[i * 2 + 1])
            }
            mesh.indices.forEach(out::writeInt)
        }
    }

    fun readBinary(file: File): ReplicaMesh =
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            require(input.readInt() == MAGIC) { "Not an ERAYA replica mesh" }
            require(input.readInt() == VERSION) { "Unsupported replica mesh version" }
            val vertices = input.readInt()
            val indexCount = input.readInt()
            require(vertices in 3..10_000 && indexCount in 3..100_000 && indexCount % 3 == 0) {
                "Replica mesh header is invalid"
            }
            val positions = FloatArray(vertices * 3)
            val normals = FloatArray(vertices * 3)
            val textureCoordinates = FloatArray(vertices * 2)
            repeat(vertices) { i ->
                positions[i * 3] = input.readFloat()
                positions[i * 3 + 1] = input.readFloat()
                positions[i * 3 + 2] = input.readFloat()
                normals[i * 3] = input.readFloat()
                normals[i * 3 + 1] = input.readFloat()
                normals[i * 3 + 2] = input.readFloat()
                textureCoordinates[i * 2] = input.readFloat()
                textureCoordinates[i * 2 + 1] = input.readFloat()
            }
            val indices = IntArray(indexCount) { input.readInt() }
            ReplicaMesh(positions, normals, textureCoordinates, indices)
        }

    fun writeObj(obj: File, material: File, textureName: String, mesh: ReplicaMesh) {
        material.writeText(
            """
            newmtl ErayaReplica
            Ka 0.200000 0.200000 0.200000
            Kd 1.000000 1.000000 1.000000
            Ks 0.080000 0.080000 0.080000
            Ns 18.000000
            map_Kd $textureName
            """.trimIndent() + "\n",
        )
        obj.bufferedWriter().use { out ->
            out.appendLine("# ERAYA private on-device facial relief mesh")
            out.appendLine("mtllib ${material.name}")
            out.appendLine("o ErayaReplicaFace")
            for (i in 0 until mesh.vertexCount) {
                out.appendLine(format("v %.7f %.7f %.7f", mesh.positions[i * 3], mesh.positions[i * 3 + 1], mesh.positions[i * 3 + 2]))
            }
            for (i in 0 until mesh.vertexCount) {
                out.appendLine(format("vt %.7f %.7f", mesh.textureCoordinates[i * 2], mesh.textureCoordinates[i * 2 + 1]))
            }
            for (i in 0 until mesh.vertexCount) {
                out.appendLine(format("vn %.7f %.7f %.7f", mesh.normals[i * 3], mesh.normals[i * 3 + 1], mesh.normals[i * 3 + 2]))
            }
            out.appendLine("usemtl ErayaReplica")
            for (i in mesh.indices.indices step 3) {
                val a = mesh.indices[i] + 1
                val b = mesh.indices[i + 1] + 1
                val c = mesh.indices[i + 2] + 1
                out.appendLine("f $a/$a/$a $b/$b/$b $c/$c/$c")
            }
        }
    }

    fun calculateNormals(positions: FloatArray, indices: IntArray): FloatArray {
        val normals = FloatArray(positions.size)
        for (i in indices.indices step 3) {
            val ia = indices[i] * 3
            val ib = indices[i + 1] * 3
            val ic = indices[i + 2] * 3
            val abx = positions[ib] - positions[ia]
            val aby = positions[ib + 1] - positions[ia + 1]
            val abz = positions[ib + 2] - positions[ia + 2]
            val acx = positions[ic] - positions[ia]
            val acy = positions[ic + 1] - positions[ia + 1]
            val acz = positions[ic + 2] - positions[ia + 2]
            val nx = aby * acz - abz * acy
            val ny = abz * acx - abx * acz
            val nz = abx * acy - aby * acx
            for (v in intArrayOf(ia, ib, ic)) {
                normals[v] += nx
                normals[v + 1] += ny
                normals[v + 2] += nz
            }
        }
        for (i in normals.indices step 3) {
            val length = sqrt(normals[i] * normals[i] + normals[i + 1] * normals[i + 1] + normals[i + 2] * normals[i + 2])
            if (length > 1e-6f) {
                normals[i] /= length
                normals[i + 1] /= length
                normals[i + 2] /= length
            } else {
                normals[i + 2] = 1f
            }
        }
        return normals
    }

    private fun format(pattern: String, vararg values: Any): String =
        String.format(Locale.US, pattern, *values)
}
