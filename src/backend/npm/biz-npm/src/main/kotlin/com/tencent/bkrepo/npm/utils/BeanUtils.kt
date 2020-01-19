package com.tencent.bkrepo.npm.utils

import com.google.common.collect.Maps
import com.google.gson.JsonArray
import org.springframework.cglib.beans.BeanMap

object BeanUtils {
    /**
	 * 将对象转换为map
	 *
	 * @param bean
	 * @return
	 */
    fun <T> beanToMap(bean: T?): Map<String, String> {
        val map = Maps.newHashMap<String, String>()
        if (bean != null) {
            val beanMap = BeanMap.create(bean)
            for (key in beanMap.keys) {
                var value = beanMap[key] ?: continue
                // if(StringUtils.isEmpty(value as String)) continue
                if (value is JsonArray) {
                    value = GsonUtils.gson.toJson(value)
                }
                map[key.toString()] = value as String
            }
        }
        return map
    }

    /**
	 * 将map转换为javabean对象
	 *
	 * @param map
	 * @param bean
	 * @return
	 */
    fun <T> mapToBean(map: Map<String, Any>, bean: T): T {
        val beanMap = BeanMap.create(bean)
        beanMap.putAll(map)
        return bean
    }
}
