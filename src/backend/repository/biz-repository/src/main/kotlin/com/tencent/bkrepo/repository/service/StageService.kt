package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.repository.pojo.stage.ArtifactStageEnum

/**
 * 制品晋级服务接口
 */
interface StageService {

    /**
     * 查询构件制品状态
     */
    fun query(artifactInfo: ArtifactInfo): ArtifactStageEnum

    /**
     * 晋级构件
     */
    fun upgrade(artifactInfo: ArtifactInfo, tag: String?)
}
