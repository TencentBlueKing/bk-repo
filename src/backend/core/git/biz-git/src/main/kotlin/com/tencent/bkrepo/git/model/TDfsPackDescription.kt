package com.tencent.bkrepo.git.model

import com.tencent.bkrepo.git.model.TDfsPackDescription.Companion.PACK_NAME_IDX
import com.tencent.bkrepo.git.model.TDfsPackDescription.Companion.PACK_NAME_IDX_DEF
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document

@Document("code_pack_desc")
@CompoundIndex(def = PACK_NAME_IDX_DEF, name = PACK_NAME_IDX, background = true, unique = true)
data class TDfsPackDescription(
    var id: String? = null,
    val createdBy: String? = null,
    val createdDate: LocalDateTime? = null,
    val projectId: String,
    val repoName: String,
    val packName: String,
    val packSource: String,
    val repoDesc: String,
    val extensions: Int,
    val sizeMap: String,
    val blockSizeMap: String,
    val objectCount: Long,
    val deltaCount: Long,
    val minUpdateIndex: Long,
    val maxUpdateIndex: Long,
    val indexVersion: Int,
    val estimatedPackSize: Long
) {
    companion object {
        const val PACK_NAME_IDX = "projectId_repoName_packName"
        const val PACK_NAME_IDX_DEF = "{'projectId':1 , 'repoName':1,'packName':1}"
    }
}
