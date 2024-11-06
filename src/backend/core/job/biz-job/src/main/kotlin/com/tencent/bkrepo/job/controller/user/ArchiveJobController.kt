package com.tencent.bkrepo.job.controller.user

import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.job.pojo.ArchiveRestoreRequest
import com.tencent.bkrepo.job.service.ArchiveJobService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 归档任务服务接口
 * */
@RestController
@RequestMapping("/api/job/archive")
@Principal(type = PrincipalType.ADMIN)
class ArchiveJobController(
    private val archiveJobService: ArchiveJobService,
) {
    @PostMapping
    fun archive(
        @RequestParam projectId: String,
        @RequestParam key: String,
        @RequestParam days: Int,
        @RequestParam storageClass: ArchiveStorageClass,
    ) {
        archiveJobService.archive(projectId, key, days, storageClass)
    }

    @PostMapping("/restore")
    fun restore(@RequestBody request: ArchiveRestoreRequest) {
        archiveJobService.restore(request)
    }
}
