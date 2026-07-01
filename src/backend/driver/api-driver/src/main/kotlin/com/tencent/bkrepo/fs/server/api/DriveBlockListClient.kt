/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 */

package com.tencent.bkrepo.fs.server.api

import com.tencent.bkrepo.common.api.constant.FS_SERVER_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.fs.server.pojo.DriveFileBlockInfo
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(FS_SERVER_SERVICE_NAME, url = "\${fs-service-url:}", contextId = "DriveBlockListClient", primary = false)
@RequestMapping("/service/drive/block")
interface DriveBlockListClient {

    @GetMapping("/list/{projectId}/{repoName}")
    fun listBlocks(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam path: String,
    ): Response<DriveFileBlockInfo>
}
