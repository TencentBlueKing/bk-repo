package com.tencent.bkrepo.generic.configuation

import org.springframework.boot.web.servlet.filter.OrderedHiddenHttpMethodFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.HiddenHttpMethodFilter
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Configuration
class Configuration {
    @Bean
    fun hiddenHttpMethodFilter(): HiddenHttpMethodFilter {
        return object: OrderedHiddenHttpMethodFilter() {
            override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
                if(request.method == "PUT" && request.requestURI.startsWith("/artifactory")){
                    logger.info("hiddenHttpMethodFilter, requestURI: ${request.requestURI}")
                    filterChain.doFilter(request, response);
                } else {
                    super.doFilterInternal(request, response, filterChain)
                }
            }
        }
    }
}