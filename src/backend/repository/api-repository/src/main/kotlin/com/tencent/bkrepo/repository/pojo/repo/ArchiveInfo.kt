package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.common.api.util.HumanReadable

data class ArchiveInfo(
    val available: Long,
    val availableHumanReadable: String = HumanReadable.size(available),
)
