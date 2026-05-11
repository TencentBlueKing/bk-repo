package com.tencent.bkrepo.repository.service.clientupgrade

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientVersionConfigUpsertRequest
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientVersionConfigVo
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientUpgradeCheckResponse

interface ClientVersionConfigService {
    fun upsert(userId: String, request: ClientVersionConfigUpsertRequest)

    fun batchUpsert(userId: String, requests: List<ClientVersionConfigUpsertRequest>)

    fun remove(id: String)

    fun batchRemove(ids: List<String>)

    fun listPage(productId: String?, pageNumber: Int, pageSize: Int): Page<ClientVersionConfigVo>

    fun checkUpgrade(
        forUserId: String,
        currentVersion: String,
        productId: String,
        platform: String,
        arch: String,
    ): ClientUpgradeCheckResponse
}
