package com.tencent.bkrepo.auth.resource

import com.tencent.bk.sdk.iam.dto.callback.request.CallbackRequestDTO
import com.tencent.bk.sdk.iam.dto.callback.response.CallbackBaseResponseDTO
import com.tencent.bkrepo.auth.api.BkiamCallbackResource
import com.tencent.bkrepo.auth.pojo.bkiam.BkResult
import com.tencent.bkrepo.auth.service.bkiam.BkiamService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class BkiamCallbackResourceImpl @Autowired constructor(
    private val bkiamService: BkiamService
) : BkiamCallbackResource {
    override fun queryProject(token: String, request: CallbackRequestDTO): CallbackBaseResponseDTO? {
        return bkiamService.queryProject(token, request)
    }

    override fun queryRepo(token: String, request: CallbackRequestDTO): CallbackBaseResponseDTO? {
        return bkiamService.queryRepo(token, request)
    }

    override fun health(): BkResult<Boolean> {
        return BkResult(true)
    }
}
