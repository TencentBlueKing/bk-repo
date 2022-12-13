/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.auth.config

import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_NAME
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_VALUE_BKIAMV3
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_VALUE_DEVOPS
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_VALUE_LOCAL
import com.tencent.bkrepo.auth.repository.AccountRepository
import com.tencent.bkrepo.auth.repository.OauthTokenRepository
import com.tencent.bkrepo.auth.repository.PermissionRepository
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.auth.service.bkauth.BkAuthPermissionServiceImpl
import com.tencent.bkrepo.auth.service.bkauth.BkAuthPipelineService
import com.tencent.bkrepo.auth.service.bkauth.BkAuthProjectService
import com.tencent.bkrepo.auth.service.bkiamv3.BkIamV3PermissionServiceImpl
import com.tencent.bkrepo.auth.service.bkiamv3.BkIamV3Service
import com.tencent.bkrepo.auth.service.local.AccountServiceImpl
import com.tencent.bkrepo.auth.service.local.PermissionServiceImpl
import com.tencent.bkrepo.auth.service.local.RoleServiceImpl
import com.tencent.bkrepo.auth.service.local.UserServiceImpl
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.core.Ordered
import org.springframework.data.mongodb.core.MongoTemplate

@Configuration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
class AuthServiceConfig {

    @Autowired
    @Lazy
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
    @Lazy
    private lateinit var projectClient: ProjectClient

    @Bean
    @ConditionalOnMissingBean(AccountService::class)
    fun accountService(
        accountRepository: AccountRepository,
        oauthTokenRepository: OauthTokenRepository,
        userService: UserService,
        mongoTemplate: MongoTemplate
    ) = AccountServiceImpl(accountRepository, oauthTokenRepository, userService, mongoTemplate)

    @Bean
    @ConditionalOnProperty(
        prefix = AUTH_CONFIG_PREFIX,
        name = [AUTH_CONFIG_TYPE_NAME],
        havingValue = AUTH_CONFIG_TYPE_VALUE_LOCAL,
        matchIfMissing = true
    )
    fun permissionService(
        userRepository: UserRepository,
        roleRepository: RoleRepository,
        accountRepository: AccountRepository,
        permissionRepository: PermissionRepository,
        mongoTemplate: MongoTemplate
    ): PermissionService {
        return PermissionServiceImpl(
            userRepository,
            roleRepository,
            accountRepository,
            permissionRepository,
            mongoTemplate,
            repositoryClient,
            projectClient
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = AUTH_CONFIG_PREFIX, name = [AUTH_CONFIG_TYPE_NAME], havingValue = AUTH_CONFIG_TYPE_VALUE_BKIAMV3
    )
    fun bkiamV3PermissionService(
        bkiamV3Service: BkIamV3Service,
        userRepository: UserRepository,
        roleRepository: RoleRepository,
        accountRepository: AccountRepository,
        permissionRepository: PermissionRepository,
        mongoTemplate: MongoTemplate,
        bkAuthConfig: BkAuthConfig,
        bkAuthPipelineService: BkAuthPipelineService,
        bkAuthProjectService: BkAuthProjectService
    ): PermissionService {
        return BkIamV3PermissionServiceImpl(
            userRepository,
            roleRepository,
            accountRepository,
            permissionRepository,
            mongoTemplate,
            bkiamV3Service,
            repositoryClient,
            projectClient,
            bkAuthConfig,
            bkAuthPipelineService,
            bkAuthProjectService
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = AUTH_CONFIG_PREFIX, name = [AUTH_CONFIG_TYPE_NAME], havingValue = AUTH_CONFIG_TYPE_VALUE_DEVOPS
    )
    fun bkAuthPermissionService(
        userRepository: UserRepository,
        roleRepository: RoleRepository,
        accountRepository: AccountRepository,
        permissionRepository: PermissionRepository,
        mongoTemplate: MongoTemplate,
        bkAuthConfig: BkAuthConfig,
        bkAuthPipelineService: BkAuthPipelineService,
        bkAuthProjectService: BkAuthProjectService
    ): PermissionService {
        return BkAuthPermissionServiceImpl(
            userRepository,
            roleRepository,
            accountRepository,
            permissionRepository,
            mongoTemplate,
            bkAuthConfig,
            bkAuthPipelineService,
            bkAuthProjectService,
            repositoryClient,
            projectClient
        )
    }

    @Bean
    @ConditionalOnMissingBean(RoleService::class)
    fun roleService(
        roleRepository: RoleRepository,
        userService: UserService,
        userRepository: UserRepository,
        mongoTemplate: MongoTemplate
    ) = RoleServiceImpl(roleRepository, userService, userRepository, mongoTemplate)

    @Bean
    @ConditionalOnMissingBean(UserService::class)
    fun userService(
        userRepository: UserRepository,
        roleRepository: RoleRepository,
        mongoTemplate: MongoTemplate
    ) = UserServiceImpl(userRepository, roleRepository, mongoTemplate)
}
