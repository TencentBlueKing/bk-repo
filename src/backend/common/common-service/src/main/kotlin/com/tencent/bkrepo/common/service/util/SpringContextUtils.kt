package com.tencent.bkrepo.common.service.util

import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

class SpringContextUtils : ApplicationContextAware {
    /**
     * 实现ApplicationContextAware接口的回调方法，设置上下文环境
     */
    @Throws(BeansException::class)
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        Companion.applicationContext = applicationContext
    }

    companion object {

        private lateinit var applicationContext: ApplicationContext

        /**
         * 获取对象
         * @param <T> Bean
         * @return 实例
         * @throws BeansException 异常
         */
        @Throws(BeansException::class)
        inline fun <reified T> getBean(): T {
            return getBean(T::class.java)
        }

        /**
         * 获取对象 这里重写了bean方法，起主要作用
         * @param clazz 类名
         * @param <T> Bean
         * @return 实例
         * @throws BeansException 异常
         */
        @Throws(BeansException::class)
        fun <T> getBean(clazz: Class<T>): T {
            return applicationContext.getBean(clazz)
        }

        /**
         * 取指定类的指定名称的类的实例对象
         * @param clazz 类名
         * @param beanName 实例对象名称
         * @param <T> Bean
         * @return 实例
         * @throws BeansException 异常
         */
        @Throws(BeansException::class)
        fun <T> getBean(clazz: Class<T>, beanName: String): T {
            return applicationContext.getBean(beanName, clazz)
        }

        /**
         * 获取对象列表
         * @param clazz 注解类名
         * @param <T: Annotation> 注解
         * @return 实例列表
         * @throws BeansException 异常
         */
        @Throws(BeansException::class)
        fun <T : Annotation> getBeansWithAnnotation(clazz: Class<T>): List<Any> {
            return applicationContext.getBeansWithAnnotation(clazz).values.toList()
        }
    }
}
