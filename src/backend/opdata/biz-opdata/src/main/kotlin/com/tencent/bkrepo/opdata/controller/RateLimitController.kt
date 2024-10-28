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
import org.springframework.web.bind.annotation.RequestParam

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
        val tRateLimit = rateLimiterConfigService.findByModuleNameAndLimitDimensionAndResource(
            request.resource,
            request.moduleName,
            request.limitDimension
        )
        if (tRateLimit == null || !tRateLimit.id.equals(request.id)) {
            checkResource(request)
        }
        rateLimiterConfigService.update(request)
        return ResponseBuilder.success()
    }

    private fun checkResource(request: RateLimitCreatOrUpdateRequest) {
        with(request) {
            val tRateLimits = rateLimiterConfigService.findByResourceAndLimitDimension(
                resource = resource,
                limitDimension = limitDimension
            )
            val modules = ArrayList<String>()
            tRateLimits.forEach { tRateLimit ->
                if(id == null || !tRateLimit.id.equals(id)) {
                    modules.addAll(tRateLimit.moduleName)
                    }
                }
            if (modules.isNotEmpty()) {
                modules.retainAll(moduleName)
                if (modules.isNotEmpty()) {
                    throw ErrorCodeException(
                        CommonMessageCode.RESOURCE_EXISTED,
                        "resource:$resource,limitDimension:$limitDimension,module:${modules}"
                    )
                }
            }
        }
    }

    // 新增
    @PostMapping("/create")
    fun create(@RequestBody request:RateLimitCreatOrUpdateRequest): Response<Void> {
        checkResource(request)
        rateLimiterConfigService.create(request)
        return ResponseBuilder.success()
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

    // 获取数据库里面的模块名
    @PostMapping("/getExistModule")
    fun getExistModule(@RequestParam resource:String,@RequestParam limitDimension:String): Response<List<String>> {
        val tRateLimits = rateLimiterConfigService.findByResourceAndLimitDimension(
            resource = resource,
            limitDimension = limitDimension
        )
        val modules = ArrayList<String>()
        tRateLimits.forEach { tRateLimit -> modules.addAll(tRateLimit.moduleName) }
        return ResponseBuilder.success(modules)
    }

}