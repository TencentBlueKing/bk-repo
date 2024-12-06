package com.tencent.bkrepo.media.web

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

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
