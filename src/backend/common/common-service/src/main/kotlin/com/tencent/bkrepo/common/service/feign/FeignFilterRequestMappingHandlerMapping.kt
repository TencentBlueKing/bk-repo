package com.tencent.bkrepo.common.service.feign

import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * 重写RequestMappingHandlerMapping，避免声明Feign Client Api时的RequestMapping注解
 * 与Feign Client Api 实现类的RestController注解重复，造成可能无法启动，以及swagger会重复扫描的问题
 *
 * @author: carrypan
 * @date: 2019/11/1
 */
class FeignFilterRequestMappingHandlerMapping : RequestMappingHandlerMapping() {

    override fun isHandler(beanType: Class<*>): Boolean {
        return AnnotatedElementUtils.hasAnnotation(beanType, Controller::class.java) || AnnotatedElementUtils.hasAnnotation(beanType, RestController::class.java)
    }
}
