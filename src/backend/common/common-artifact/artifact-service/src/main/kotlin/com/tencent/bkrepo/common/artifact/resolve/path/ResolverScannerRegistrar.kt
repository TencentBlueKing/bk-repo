package com.tencent.bkrepo.common.artifact.resolve.path

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanClassLoaderAware
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.EnvironmentAware
import org.springframework.context.ResourceLoaderAware
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.env.Environment
import org.springframework.core.io.ResourceLoader
import org.springframework.core.type.AnnotationMetadata
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.util.ClassUtils

/**
 * 自动扫描@Resolver注解
 */
class ResolverScannerRegistrar : ImportBeanDefinitionRegistrar, ResourceLoaderAware, BeanClassLoaderAware, EnvironmentAware {

    private lateinit var resourceLoader: ResourceLoader
    private lateinit var environment: Environment
    private lateinit var classLoader: ClassLoader

    override fun registerBeanDefinitions(annotationMetadata: AnnotationMetadata, beanDefinitionRegistry: BeanDefinitionRegistry) {
        logger.info("Scanning ArtifactInfo resolver.")
        val provider = createResolverScanner()
        provider.resourceLoader = resourceLoader
        val basePackages = listOf(ClassUtils.getPackageName(this.javaClass), ClassUtils.getPackageName(annotationMetadata.className))
        basePackages.forEach {
            for (beanDefinition in provider.findCandidateComponents(it)) {
                val clazz = Class.forName(beanDefinition.beanClassName)
                if (ArtifactInfoResolver::class.java.isAssignableFrom(clazz)) {
                    val annotation = clazz.getAnnotation(Resolver::class.java)
                    val instance = clazz.newInstance() as ArtifactInfoResolver
                    if (!resolverMap.containsKey(annotation.value)) {
                        resolverMap.register(annotation.value, instance, annotation.default)
                        logger.debug("Registering ArtifactInfo resolver: [${annotation.value} -> ${beanDefinition.beanClassName} (default: ${annotation.default})].")
                    }
                }
            }
        }
    }

    private fun createResolverScanner(): ClassPathScanningCandidateComponentProvider {
        val provider = ClassPathScanningCandidateComponentProvider(false)
        provider.addIncludeFilter(AnnotationTypeFilter(Resolver::class.java))
        return provider
    }

    override fun setResourceLoader(resourceLoader: ResourceLoader) {
        this.resourceLoader = resourceLoader
    }

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    override fun setBeanClassLoader(classLoader: ClassLoader) {
        this.classLoader = classLoader
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResolverScannerRegistrar::class.java)
        val resolverMap = ResolverMap()
    }
}
