/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 */

package com.tencent.bkrepo.fs.server.request.service

import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.queryParamOrNull

class DriveBlockListRequest(request: ServerRequest) {
    val projectId: String = request.pathVariable(PROJECT_ID)
    val repoName: String = request.pathVariable(REPO_NAME)
    val path: String

    init {
        path = request.queryParamOrNull("path") ?: throw ParameterInvalidException("required path parameter.")
    }
}
