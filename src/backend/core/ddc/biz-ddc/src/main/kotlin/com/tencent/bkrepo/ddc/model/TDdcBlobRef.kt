package com.tencent.bkrepo.ddc.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document


@Document("ddc_blob_ref")
@CompoundIndexes(
    CompoundIndex(
        name = "projectId_repoName_blobId_ref_idx",
        def = "{'projectId': 1, 'repoName': 1, 'blobId': 1, 'ref': 1}",
        unique = true,
        background = true
    ),
    CompoundIndex(
        name = "projectId_repoName_ref_blobId_idx",
        def = "{'projectId': 1, 'repoName': 1, 'ref': 1, 'blobId': 1}",
        unique = true,
        background = true
    ),
)
data class TDdcBlobRef(
    var id: String? = null,
    var projectId: String,
    var repoName: String,
    /**
     * blob blake3 hash
     */
    var blobId: String,
    /**
     * 引用了该blob的ref或blob，ref的inline blob中直接或间接引用的所有blob都会关联到ref
     * ref类型引用 ref/{bucket}/{key}
     * blob类型引用 blob/{blobId}
     */
    var ref: String,
)
