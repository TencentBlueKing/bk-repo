package com.tencent.bkrepo.common.security.http.login

import com.tencent.bkrepo.common.security.http.HttpAuthSecurity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import javax.annotation.PostConstruct
import kotlin.reflect.jvm.javaMethod

@Configuration
class LoginConfiguration {

    @Autowired
    private lateinit var requestMappingHandlerMapping: RequestMappingHandlerMapping

    @Autowired
    private lateinit var httpAuthSecurity: HttpAuthSecurity

    @PostConstruct
    fun init() {
        httpAuthSecurity.getAuthHandlerList().forEach { handler ->
            handler.getLoginEndpoint()?.let { registerLoginEndpoint(it) }
        }
    }

    private fun registerLoginEndpoint(endpoint: String) {
        val mappingInfo = RequestMappingInfo.paths(endpoint).build()
        requestMappingHandlerMapping.registerMapping(mappingInfo, this, this::anonymous.javaMethod!!)
    }

    /**
     * a trick method for registering request mapping dynamiclly in spring interceptor
     */
    @ResponseBody
    private fun anonymous() { }
}
