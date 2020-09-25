package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectRangeQueryRequest

/**
 * 项目服务接口
 */
interface ProjectService {

    /**
     * 查询名称为[name]的项目信息
     */
    fun query(name: String): ProjectInfo?

    /**
     * 查询项目列表
     */
    fun list(): List<ProjectInfo>

    /**
     * 分页查询项目列表
     */
    fun rangeQuery(request: ProjectRangeQueryRequest): Page<ProjectInfo?>

    /**
     * 判断名称为[name]的项目是否存在
     */
    fun exist(name: String): Boolean

    /**
     * 根据[request]创建项目，创建成功后返回项目信息
     */
    fun create(request: ProjectCreateRequest): ProjectInfo

    /**
     * 校验名称为[name]的项目是否存在
     * 如果不存在则抛[ErrorCodeException]，`code`为`ArtifactMessageCode.PROJECT_NOT_FOUND`
     */
    @Throws(ErrorCodeException::class)
    fun checkProject(name: String)
}
