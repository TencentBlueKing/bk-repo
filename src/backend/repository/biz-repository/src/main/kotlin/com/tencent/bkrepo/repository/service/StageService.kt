package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.stage.StageUpgradeRequest

/**
 * 包版本晋级服务接口
 */
interface StageService {

    /**
     * 查询版本晋级状态
     */
    fun query(projectId: String, repoName: String, packageKey: String, version: String): List<String>

    /**
     * 版本晋级
     */
    fun upgrade(request: StageUpgradeRequest)
}
