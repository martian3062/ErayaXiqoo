package com.evolet.tachyon.twin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ReplicaStoreTest {
    @Test
    fun capturePlanHasTenUniqueGuidedViews() {
        val poses = ReplicaCapturePlan.poses
        assertEquals(10, poses.size)
        assertEquals(10, poses.map { it.id }.toSet().size)
        assertTrue(poses.all { it.label.isNotBlank() && it.instruction.isNotBlank() })
    }

    @Test
    fun captureIsCompleteOnlyWithEveryViewAndMotion() {
        val all = ReplicaCapturePlan.poses.mapTo(linkedSetOf()) { it.id }
        assertFalse(ReplicaSnapshot(capturedPoseIds = all, hasMotionVideo = false).captureComplete)
        assertFalse(ReplicaSnapshot(capturedPoseIds = all.drop(1).toSet(), hasMotionVideo = true).captureComplete)
        assertTrue(ReplicaSnapshot(capturedPoseIds = all, hasMotionVideo = true).captureComplete)
    }

    @Test
    fun unrelatedFilesCannotSatisfyCapturePlan() {
        val ids = ReplicaCapturePlan.poses.dropLast(1).mapTo(linkedSetOf()) { it.id }
        ids += "unknown"
        assertFalse(ReplicaSnapshot(capturedPoseIds = ids, hasMotionVideo = true).captureComplete)
    }

    @Test
    fun replicaMeshRoundTripsAndExportsBlenderObj() {
        val positions = floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, 0f, 1f, 0.25f)
        val indices = intArrayOf(0, 1, 2)
        val mesh = ReplicaMesh(
            positions = positions,
            normals = ReplicaMeshCodec.calculateNormals(positions, indices),
            textureCoordinates = floatArrayOf(0f, 0f, 1f, 0f, 0.5f, 1f),
            indices = indices,
        )
        val dir = Files.createTempDirectory("replica-mesh-test").toFile()
        try {
            val binary = dir.resolve("replica.mesh")
            val obj = dir.resolve("replica.obj")
            val material = dir.resolve("replica.mtl")
            ReplicaMeshCodec.writeBinary(binary, mesh)
            ReplicaMeshCodec.writeObj(obj, material, "replica_texture.jpg", mesh)

            val loaded = ReplicaMeshCodec.readBinary(binary)
            assertEquals(3, loaded.vertexCount)
            assertEquals(1, loaded.triangleCount)
            assertArrayEquals(mesh.positions, loaded.positions, 0f)
            assertArrayEquals(mesh.indices, loaded.indices)
            assertTrue(obj.readText().contains("f 1/1/1 2/2/2 3/3/3"))
            assertTrue(material.readText().contains("map_Kd replica_texture.jpg"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
