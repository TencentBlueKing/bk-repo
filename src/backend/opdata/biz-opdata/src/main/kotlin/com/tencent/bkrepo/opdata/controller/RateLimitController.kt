package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.model.RateLimitCreatOrUpdateRequest
import com.tencent.bkrepo.common.ratelimiter.model.TRateLimit
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PathVariable

@RestController
@RequestMapping("/api/rateLimit")
@Principal(PrincipalType.ADMIN)
class RateLimitController(
    private val rateLimiterConfigService: RateLimiterConfigService,
    private val rateLimiterProperties: RateLimiterProperties
) {

    @GetMapping("/list")
    fun list(): Response<List<TRateLimit>> {
        return ResponseBuilder.success(rateLimiterConfigService.list())
    }

    @PostMapping("/update")
    fun update(@RequestBody request: RateLimitCreatOrUpdateRequest): Response<Void> {
        if (request.id.isNullOrBlank()) {
            throw NotFoundException(CommonMessageCode.PARAMETER_EMPTY, "ID")
        }
        if (!rateLimiterConfigService.checkExist(request.id!!)) {
            throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, request.id!!)
        }
        rateLimiterConfigService.getById(request.id!!).let {
            if (it != null &&
                (!it.resource.equals(request.resource) || !it.limitDimension.equals(request.limitDimension)) &&
                rateLimiterConfigService.checkExist(request)
            ) {
                throw ErrorCodeException(
                    CommonMessageCode.RESOURCE_EXISTED,
                    "resource:${request.resource},limitDimension:${request.limitDimension})"
                )
            }
        }
        rateLimiterConfigService.update(request)
        return ResponseBuilder.success()
    }

    // 新增
    @PostMapping("/create")
    fun create(@RequestBody request:RateLimitCreatOrUpdateRequest): Response<Void> {
        with(request) {
            if (rateLimiterConfigService.checkExist(request)) {
                throw ErrorCodeException(
                    CommonMessageCode.RESOURCE_EXISTED,
                    "resource:$resource,limitDimension:$limitDimension"
                )
            }
            rateLimiterConfigService.create(request)
            return ResponseBuilder.success()
        }
    }

    // 删除
    @DeleteMapping("/delete/{id}")
    fun delete(@PathVariable id:String): Response<Void> {
        if (!rateLimiterConfigService.checkExist(id)) {
            throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        }
        rateLimiterConfigService.delete(id)
        return ResponseBuilder.success()
    }

    // 获取配置中的属性
    @GetMapping("/config")
    fun getConfig(): Response<String> {
        val config = rateLimiterProperties
        return ResponseBuilder.success(config.rules.toJsonString())
    }

}