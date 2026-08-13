/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.tool.domain

import io.agentscope.core.tool.Toolkit
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DomainToolRegistrar(
    private val toolkit: Toolkit,
    domainTools: List<AbstractReadOnlyDomainTool>,
) : RegisteredDomainTools {
    private val domainTools: List<AbstractReadOnlyDomainTool> = domainTools

    override val registeredToolNames: Set<String> = domainTools.map { it.name }.toSet()

    init {
        val duplicateNames = domainTools.groupBy { it.name }.filter { it.value.size > 1 }.keys
        require(duplicateNames.isEmpty()) { "Duplicate domain tool names: $duplicateNames" }
    }

    @PostConstruct
    fun register() {
        domainTools.forEach { toolkit.registerAgentTool(it) }
        logger.info("registered {} domain tools: {}", domainTools.size, registeredToolNames.sorted())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DomainToolRegistrar::class.java)
    }
}
