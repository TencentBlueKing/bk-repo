package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.api.constant.StringPool
import java.util.concurrent.atomic.AtomicInteger

data class DownloadSession(
    val id: String = StringPool.uniqueId(),
    var latencyTime: Long = 0,
    val activeCount: AtomicInteger,
    var closed: Boolean = false,
)
