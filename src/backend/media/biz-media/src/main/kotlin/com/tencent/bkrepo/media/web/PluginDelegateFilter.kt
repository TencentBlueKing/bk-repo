package com.tencent.bkrepo.media.web

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

class PluginDelegateFilter : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (delegate != null) {
            delegate!!.doFilter(request, response, chain)
        } else {
            chain.doFilter(request, response)
        }
    }

    companion object {
        var delegate: Filter? = null
    }
}
