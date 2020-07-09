package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode

abstract class AbstractPermissionResourceImpl {

    fun checkRequest(request: CheckPermissionRequest) {
        with(request) {
            when (resourceType) {
                ResourceType.SYSTEM -> {
                }
                ResourceType.PROJECT -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "projectId")
                    }
                }
                ResourceType.REPO -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "projectId")
                    }
                    if (repoName.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "repoId")
                    }
                }
                ResourceType.NODE -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "node")
                    }
                    if (repoName.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "repoId")
                    }
                    if (path.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "node")
                    }
                }
            }
        }
    }
}
