package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.permission.Principal
import com.tencent.bkrepo.common.artifact.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.job.FileSynchronizeJob
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/job")
class SystemJobController {

    @Autowired
    private lateinit var fileSynchronizeJob: FileSynchronizeJob

    @Principal(type = PrincipalType.ADMIN)
    @GetMapping("/synchronizeFile")
    fun synchronizeFile(): Response<Void> {
        fileSynchronizeJob.run()
        return ResponseBuilder.success()
    }
}
