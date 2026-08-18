package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlockNodeUploadIdTest {

    @Test
    fun replaceMetadataKeepsSessionAndMarksSystem() {
        val metadata = listOf(
            MetadataModel(key = "user-key", value = "v", system = false),
            MetadataModel(key = BlockNodeUploadId.KEY, value = "old/src-id", system = true),
        )
        val replaced = BlockNodeUploadId.replaceMetadata(metadata, "src-session/src-id")
        assertEquals("v", replaced.first { it.key == "user-key" }.value)
        val uploadId = replaced.first { it.key == BlockNodeUploadId.KEY }
        assertEquals("src-session/src-id", uploadId.value)
        assertEquals(true, uploadId.system)
    }

    @Test
    fun replicaSessionIsStableForSameNode() {
        assertEquals("replica/node-1", BlockNodeUploadId.replicaSession("node-1"))
        assertEquals("replica/node-1", BlockNodeUploadId.replicaSession("node-1"))
    }

    @Test
    fun finishSessionRequiresExactMatch() {
        assertEquals("replica/n1", BlockNodeUploadId.finishSession("replica/n1", "replica/n1"))
        assertEquals(null, BlockNodeUploadId.finishSession(null, "replica/n1"))
        assertEquals(null, BlockNodeUploadId.finishSession("replica/n1", "uuid-session"))
        assertEquals(null, BlockNodeUploadId.finishSession("replica/n1", "replica/n2"))
    }
}
