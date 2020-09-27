package com.tencent.bkrepo.auth.resource

import com.tencent.bk.sdk.iam.dto.callback.request.CallbackRequestDTO
import com.tencent.bk.sdk.iam.dto.callback.response.CallbackBaseResponseDTO
import com.tencent.bkrepo.auth.api.BkiamCallbackResource
import com.tencent.bkrepo.auth.pojo.bkiam.BkResult
import com.tencent.bkrepo.auth.service.bkiam.BkiamCallbackService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class BkiamCallbackResourceImpl @Autowired constructor(
    private val bkiamCallbackService: BkiamCallbackService
) : BkiamCallbackResource {
    override fun queryProject(token: String, request: CallbackRequestDTO): CallbackBaseResponseDTO? {
        return bkiamCallbackService.queryProject(token, request)
    }

    override fun queryRepo(token: String, request: CallbackRequestDTO): CallbackBaseResponseDTO? {
        return bkiamCallbackService.queryRepo(token, request)
    }

    override fun health(): BkResult<Boolean> {
        return BkResult(true)
    }
}
