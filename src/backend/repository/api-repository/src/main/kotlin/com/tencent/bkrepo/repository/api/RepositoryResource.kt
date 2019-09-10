package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import io.swagger.annotations.Api
import org.springframework.cloud.openfeign.FeignClient

/**
 * 仓库服务接口
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
@Api("仓库服务接口")
@FeignClient(contextId = "repositoryResource", value = SERVICE_NAME)
interface RepositoryResource {
}