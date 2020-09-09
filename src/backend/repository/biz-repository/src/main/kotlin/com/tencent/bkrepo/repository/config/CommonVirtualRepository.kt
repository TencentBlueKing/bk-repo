package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * 公共虚拟仓库
 */
@Primary
@Component
class CommonVirtualRepository : VirtualRepository()
