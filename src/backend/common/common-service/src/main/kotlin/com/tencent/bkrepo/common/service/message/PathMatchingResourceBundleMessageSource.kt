package com.tencent.bkrepo.common.service.message

import java.io.IOException
import java.util.Properties
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * SpringBoot默认使用基于JDK{@link java.util.ResourceBundle}实现的ResourceBundleMessageSource
 * Spring还提供另外一种基于{@link java.util.Properties}实现的ReloadableResourceBundleMessageSource
 * 以上两种实现方式从classpath加载properties时，都不支持多个同名文件同时加载，即classpath*方式，因为这种特性属于Spring，JDK本身不支持
 * 所以该类实现了支持classpath*方式加载的MessageSource
 *
 * @author: carrypan
 * @date: 2019/12/14
 */
class PathMatchingResourceBundleMessageSource : ReloadableResourceBundleMessageSource() {

    private val resolver = PathMatchingResourcePatternResolver()

    override fun refreshProperties(filename: String, propHolder: PropertiesHolder?): PropertiesHolder {
        return if (filename.startsWith(PathMatchingResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX)) {
            refreshClassPathProperties(filename, propHolder)
        } else {
            super.refreshProperties(filename, propHolder)
        }
    }

    private fun refreshClassPathProperties(filename: String, propHolder: PropertiesHolder?): PropertiesHolder {
        val properties = Properties()
        var lastModified: Long = -1
        try {
            val resources = resolver.getResources(filename + PROPERTIES_SUFFIX)
            for (resource in resources) {
                val sourcePath: String = resource.uri.toString().replace(PROPERTIES_SUFFIX, "")
                val holder = super.refreshProperties(sourcePath, propHolder)
                holder.properties?.let { properties.putAll(it) }
                if (lastModified < resource.lastModified()) {
                    lastModified = resource.lastModified()
                }
            }
        } catch (ignored: IOException) {
        }
        return PropertiesHolder(properties, lastModified)
    }

    companion object {
        private const val PROPERTIES_SUFFIX = ".properties"
    }
}
