package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import org.springframework.stereotype.Component

/**
 * 公共虚拟仓库
 */
@Component
class CommonVirtualRepository : VirtualRepository()
