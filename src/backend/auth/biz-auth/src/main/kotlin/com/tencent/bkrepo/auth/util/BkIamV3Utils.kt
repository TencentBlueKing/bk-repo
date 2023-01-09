/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.util

import com.tencent.bk.sdk.iam.config.IamConfiguration
import com.tencent.bk.sdk.iam.constants.ExpressionOperationEnum
import com.tencent.bk.sdk.iam.dto.expression.ExpressionDTO
import com.tencent.bk.sdk.iam.dto.manager.Action
import com.tencent.bk.sdk.iam.dto.manager.AuthorizationScopes
import com.tencent.bk.sdk.iam.dto.manager.ManagerPath
import com.tencent.bk.sdk.iam.dto.manager.ManagerResources
import com.tencent.bkrepo.auth.pojo.enums.ActionTypeMapping
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.iam.ResourceInfo
import com.tencent.bkrepo.common.api.constant.StringPool

object BkIamV3Utils {
    fun buildManagerResources(
        projectResInfo: ResourceInfo,
        repoResInfo: ResourceInfo? = null,
        resActionMap: Map<String, List<String>>,
        iamConfiguration: IamConfiguration
    ): List<AuthorizationScopes> {
        val authorizationScopes = mutableListOf<AuthorizationScopes>()
        resActionMap.forEach {
            authorizationScopes.add(
                buildResource(
                    projectResInfo = projectResInfo,
                    repoResInfo = repoResInfo,
                    iamConfiguration = iamConfiguration,
                    actions = it.value,
                    resourceType = it.key
                )
            )
        }
        return authorizationScopes
    }

    fun buildResource(
        projectResInfo: ResourceInfo,
        repoResInfo: ResourceInfo? = null,
        iamConfiguration: IamConfiguration,
        actions: List<String>,
        resourceType: String
    ): AuthorizationScopes {
        val realRepoResInfo = if (resourceType == ResourceType.PROJECT.id()) {
            null
        } else {
            repoResInfo
        }
        val projectManagerPath = ManagerPath(
            iamConfiguration.systemId,
            projectResInfo.resType.id(),
            projectResInfo.resId,
            projectResInfo.resName
        )
        val managerPaths = mutableListOf<ManagerPath>()
        managerPaths.add(projectManagerPath)
        realRepoResInfo?.let {
            val repoManagerPath = ManagerPath(
                iamConfiguration.systemId,
                realRepoResInfo.resType.id(),
                realRepoResInfo.resId,
                realRepoResInfo.resName
            )
            managerPaths.add(repoManagerPath)
        }
        val paths = mutableListOf<List<ManagerPath>>()
        paths.add(managerPaths)
        val resource = ManagerResources.builder()
            .system(iamConfiguration.systemId)
            .type(resourceType)
            .paths(paths)
            .build()
        val resources = mutableListOf<ManagerResources>()
        resources.add(resource)
        val action = mutableListOf<Action>()
        actions.forEach {
            action.add(Action(it))
        }
        return AuthorizationScopes
            .builder()
            .system(iamConfiguration.systemId)
            .actions(action)
            .resources(resources)
            .build()
    }

    /**
     * 节点因为分表，节点资源唯一id由数据库_id和分表index组成
     * 权限中心id限制长度为36个字符
     */
    fun buildId(first: String, second: String): String {
        val stringBuilder = StringBuilder()
        return stringBuilder.append(first).append(StringPool.COLON).append(second).toString()
    }

    fun splitId(id: String) : Pair<String, String> {
        val values = id.split(StringPool.COLON)
        return Pair(values.first(), values.last())
    }


    fun convertActionType(resourceTyp: String, action: String) : String {
        return ActionTypeMapping.lookup(resourceTyp, action).id()
    }

    fun getProjects(content: ExpressionDTO): List<String> {

        if (content.field != "project.id") {
            when(content.operator) {
                ExpressionOperationEnum.ANY,
                ExpressionOperationEnum.OR,
                ExpressionOperationEnum.AND,
                ExpressionOperationEnum.START_WITH -> {
                }
                else -> return emptyList()
            }
        }
        val projectList = mutableListOf<String>()
        when (content.operator) {
            ExpressionOperationEnum.START_WITH -> getProjectFromExpression(content)?.let {projectList.add(it) }
            ExpressionOperationEnum.ANY -> projectList.add("*")
            ExpressionOperationEnum.EQUAL -> projectList.add(content.value.toString())
            ExpressionOperationEnum.IN -> projectList.addAll(StringUtils.obj2List(content.value.toString()))
            ExpressionOperationEnum.OR, ExpressionOperationEnum.AND -> content.content.forEach {
                projectList.addAll(getProjects(it))
            }
            else -> {
            }
        }
        return projectList
    }

    // 无content怎么处理 一层怎么处理,二层怎么处理。 默认只有两层。
    fun getResourceInstance(expression: ExpressionDTO, projectId: String, resourceType: String): Set<String> {
        val instantList = mutableSetOf<String>()
        if (expression.content.isNullOrEmpty()) {
            instantList.addAll(getInstanceByField(expression, projectId, resourceType))
        } else {
            instantList.addAll(getInstanceByContent(expression.content, expression, projectId, resourceType))
        }
        return instantList
    }

    private fun getInstanceByContent(
        childExpression: List<ExpressionDTO>,
        parentExpression: ExpressionDTO,
        projectId: String,
        resourceType: String
    ): Set<String> {
        return getInstanceByContent(
            childExpression = childExpression,
            projectId = projectId,
            resourceType = resourceType,
            type = parentExpression.operator
        )
    }

    private fun getInstanceByContent(
        childExpression: List<ExpressionDTO>,
        projectId: String,
        resourceType: String,
        type: ExpressionOperationEnum
    ): Set<String> {
        val cacheList = mutableSetOf<String>()

            childExpression.map {
                if (!it.content.isNullOrEmpty()) {
                    val result = filterNonNullContent(it, projectId, resourceType, type, cacheList)
                    if (result != null) {
                        return result
                    }
                    return@map
                }

                if (!checkField(it.field, resourceType) && !checkField(it.value.toString(), resourceType)) {
                    if (!andCheck(cacheList, type)) {
                        return emptySet()
                    }
                    return@map
                }
                val compareResult = compareOperatorWithNullContent(it, projectId, type, cacheList)
                if (compareResult != null) {
                    return compareResult
                }
                if (!andCheck(cacheList, type)) {
                    return emptySet()
                }

                // 如果有“*” 表示项目下所有的实例都有权限,直接按“*”返回
                if (cacheList.contains("*")) {
                    return cacheList
                }
            }

        return cacheList
    }

    private fun filterNonNullContent(
        exp: ExpressionDTO,
        projectId: String,
        resourceType: String,
        type: ExpressionOperationEnum,
        cacheList: MutableSet<String>
    ): Set<String>?  {
        val childInstanceList = getInstanceByContent(exp.content, projectId, resourceType, exp.operator)
        if (childInstanceList.isNotEmpty()) {
            // 如果有策略解析出来为“*”,则直接返回
            if (childInstanceList.contains("*")) {
                return childInstanceList
            }
            cacheList.addAll(childInstanceList)
        } else {
            // 若表达式为AND拼接,检测到不满足条件则直接返回空数组
            if (!andCheck(cacheList, type)) {
                return emptySet()
            }
        }
        return null
    }

    private fun compareOperatorWithNullContent(
        exp: ExpressionDTO,
        projectId: String,
        type: ExpressionOperationEnum,
        cacheList: MutableSet<String>
    ): Set<String>?  {
        when (exp.operator) {
            ExpressionOperationEnum.ANY -> {
                cacheList.add("*")
                return cacheList
            }
            ExpressionOperationEnum.IN -> {
                cacheList.addAll(StringUtils.obj2List(exp.value.toString()))
                StringUtils.removeAllElement(cacheList)
            }
            ExpressionOperationEnum.EQUAL -> {
                cacheList.add(exp.value.toString())
                StringUtils.removeAllElement(cacheList)
            }
            ExpressionOperationEnum.START_WITH -> {
                // 两种情况: 1. 跟IN,EQUAL组合成项目下的某实例 2. 单指某个项目下的所有实例
                val startWithPair = checkProject(projectId, exp)
                // 跟IN,EQUAL结合才会出现type = AND
                if (type == ExpressionOperationEnum.AND) {
                    // 项目未命中直接返回空数组
                    // 命中则引用IN,EQUAL的数据,不将“*”加入到cacheList
                    if (!startWithPair.first) {
                        return emptySet()
                    }
                } else {
                    // 若为项目级别,直接按projectCheck结果返回
                    cacheList.addAll(startWithPair.second)
                }
            }
            else -> cacheList.clear()
        }
        return null
    }

    private fun getInstanceByField(expression: ExpressionDTO, projectId: String, resourceType: String): Set<String> {
        val instanceList = mutableSetOf<String>()
        val value = expression.value

        // 如果权限为整个项目, 直接返回
        if (expression.value == projectId && expression.operator == ExpressionOperationEnum.EQUAL) {
            instanceList.add("*")
            return instanceList
        }

        if (!checkField(expression.field, resourceType)) {
            return emptySet()
        }

        when (expression.operator) {
            ExpressionOperationEnum.ANY -> instanceList.add("*")
            ExpressionOperationEnum.EQUAL -> instanceList.add(value.toString())
            ExpressionOperationEnum.IN -> instanceList.addAll(StringUtils.obj2List(value.toString()))
            ExpressionOperationEnum.START_WITH -> {
                instanceList.addAll(checkProject(projectId, expression).second)
            }
            else -> {
            }
        }

        return instanceList
    }


    private fun checkProject(projectId: String, expression: ExpressionDTO): Pair<Boolean, Set<String>> {
        val instanceList = mutableSetOf<String>()
        val values = expression.value.toString().split(",")
        if (values[0] != "/project") {
            return Pair(false, emptySet())
        }
        if (values[1].substringBefore("/") != projectId) {
            return Pair(false, emptySet())
        }
        instanceList.add("*")
        return Pair(true, instanceList)
    }

    private fun getProjectFromExpression(expression: ExpressionDTO): String? {
        val values = expression.value.toString().split(",")
        if (values[0] != "/project") return null
        return values[1].substringBefore("/")
    }

    private fun checkField(field: String, resourceType: String): Boolean {
        if (field.contains(resourceType)) {
            return true
        }
        return false
    }

    private fun andCheck(instanceList: Set<String>, op: ExpressionOperationEnum): Boolean {
        if (op == ExpressionOperationEnum.AND) {
            if (instanceList.isEmpty()) {
                return false
            }
            return true
        }
        return true
    }

}
