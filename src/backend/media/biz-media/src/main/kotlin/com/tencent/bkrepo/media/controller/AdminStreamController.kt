package com.tencent.bkrepo.media.controller

import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.media.stream.StreamManger
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 管理员流控制器
 * */
@RestController
@RequestMapping("/api/admin/stream")
@Principal(PrincipalType.ADMIN)
class AdminStreamController(
    private val streamManger: StreamManger,
) {
    @PutMapping("/shutdown")
    fun shutdown() {
        streamManger.close()
    }
}
