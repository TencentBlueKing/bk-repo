package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.opdata.pojo.enums.LevelType
import com.tencent.bkrepo.opdata.pojo.enums.ForwardType
import org.springframework.data.mongodb.core.mapping.Document

@Document("internal_flow")
data class TInternalFlow(
    var id: String? = null,
    val level: LevelType,
    val name: String,
    val tag: String? = null,
    val region: String,
    val next: String?,
    val forward: ForwardType?,
    val forwardTip: String?
)