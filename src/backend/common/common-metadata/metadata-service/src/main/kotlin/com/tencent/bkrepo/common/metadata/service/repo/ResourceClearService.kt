package com.tencent.bkrepo.common.metadata.service.repo

import com.tencent.bkrepo.common.metadata.model.TRepository

/**
 * 资源清理接口
 */
interface ResourceClearService {

    /**
     * 清理仓库
     */
    fun clearRepo(repository: TRepository, forced: Boolean, operator: String)
}
