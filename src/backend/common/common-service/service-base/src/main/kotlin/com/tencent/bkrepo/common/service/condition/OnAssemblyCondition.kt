package com.tencent.bkrepo.common.service.condition

import org.springframework.boot.autoconfigure.condition.AllNestedConditions
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase

class OnAssemblyCondition : AllNestedConditions(ConfigurationPhase.REGISTER_BEAN) {

    @ConditionalOnClass(name = ["com.tencent.bkrepo.config.AssemblyApplication"])
    internal class HasClass
}
