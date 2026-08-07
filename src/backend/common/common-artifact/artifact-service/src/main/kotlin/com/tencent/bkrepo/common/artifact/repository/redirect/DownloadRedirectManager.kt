/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.repository.redirect

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DownloadRedirectManager(
    private val redirectServices: List<DownloadRedirectService>
) {
    /**
     * 配置层面是否可能重定向（不访问 node/DB）。
     * 路径需要二次解析的仓：必须先调此方法，false 则禁止查库。
     */
    fun mayRedirect(context: ArtifactDownloadContext): Boolean {
        redirectServices.forEach {
            try {
                if (it.mayRedirect(context)) {
                    return true
                }
            } catch (ignore: Exception) {
                logger.error("Check mayRedirect by ${it.javaClass.simpleName} failed", ignore)
            }
        }
        return false
    }

    /**
     * 是否需要重定向（不执行）。命中时记录 service 下标，供 [redirect] 直接执行，避免重复判断。
     */
    fun shouldRedirect(context: ArtifactDownloadContext): Boolean {
        redirectServices.forEachIndexed { index, service ->
            try {
                if (service.shouldRedirect(context)) {
                    context.putAttribute(ATTR_REDIRECT_SERVICE_INDEX, index)
                    return true
                }
            } catch (ignore: Exception) {
                logger.error("Check redirect by ${service.javaClass.simpleName} failed", ignore)
            }
        }
        return false
    }

    /**
     * 重定向下载请求（Generic 路径逻辑与原实现一致）。
     * remapper 若已 [shouldRedirect] 命中，跳过重复判断直接执行；失败则降级回 onDownload。
     */
    fun redirect(context: ArtifactDownloadContext): Boolean {
        val checkedIndex = context.getAndRemoveAttribute<Int>(ATTR_REDIRECT_SERVICE_INDEX)
        if (checkedIndex != null) {
            return try {
                redirectServices[checkedIndex].redirect(context)
                true
            } catch (ignore: Exception) {
                logger.error(
                    "Redirect by ${redirectServices[checkedIndex].javaClass.simpleName} failed",
                    ignore,
                )
                false
            }
        }
        redirectServices.forEach {
            try {
                if (it.shouldRedirect(context)) {
                    it.redirect(context)
                    return true
                }
            } catch (ignore: Exception) {
                logger.error("Redirect by ${it.javaClass.simpleName} failed", ignore)
            }
        }
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DownloadRedirectManager::class.java)
        private const val ATTR_REDIRECT_SERVICE_INDEX = "redirect.service.index"
    }
}
