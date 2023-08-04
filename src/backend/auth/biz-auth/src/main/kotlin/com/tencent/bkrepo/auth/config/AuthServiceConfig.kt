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

import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.dao.repository.AccountRepository
import com.tencent.bkrepo.auth.dao.repository.OauthTokenRepository
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.auth.service.bkauth.DevopsPermissionServiceImpl
import com.tencent.bkrepo.auth.service.bkauth.DevopsPipelineService
import com.tencent.bkrepo.auth.service.bkauth.DevopsProjectService
import com.tencent.bkrepo.auth.service.bkiam.BkiamPermissionServiceImpl
import com.tencent.bkrepo.auth.service.bkiam.BkiamService
import com.tencent.bkrepo.auth.service.local.AccountServiceImpl
import com.tencent.bkrepo.auth.service.local.PermissionServiceImpl
import com.tencent.bkrepo.auth.service.local.RoleServiceImpl
import com.tencent.bkrepo.auth.service.local.UserServiceImpl
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
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
    @ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "local", matchIfMissing = true)
    fun permissionService(
        roleRepository: RoleRepository,
        accountRepository: AccountRepository,
        permissionDao: PermissionDao,
        userDao: UserDao
    ): PermissionService {
        return PermissionServiceImpl(
            roleRepository,
            accountRepository,
            permissionDao,
            userDao,
            repositoryClient,
            projectClient
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "bkiam")
    fun bkiamPermissionService(
        roleRepository: RoleRepository,
        accountRepository: AccountRepository,
        permissionDao: PermissionDao,
        userDao: UserDao,
        bkiamService: BkiamService
    ): PermissionService {
        return BkiamPermissionServiceImpl(
            roleRepository,
            accountRepository,
            permissionDao,
            userDao,
            bkiamService,
            repositoryClient,
            projectClient
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "devops")
    fun bkAuthPermissionService(
        roleRepository: RoleRepository,
        accountRepository: AccountRepository,
        permissionDao: PermissionDao,
        userDao: UserDao,
        bkAuthConfig: DevopsAuthConfig,
        bkAuthPipelineService: DevopsPipelineService,
        bkAuthProjectService: DevopsProjectService
    ): PermissionService {
        return DevopsPermissionServiceImpl(
            roleRepository,
            accountRepository,
            permissionDao,
            userDao,
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
        userDao: UserDao
    ) = RoleServiceImpl(roleRepository, userService, userDao)

    @Bean
    @ConditionalOnMissingBean(UserService::class)
    fun userService(
        roleRepository: RoleRepository,
        userDao: UserDao
    ) = UserServiceImpl(roleRepository, userDao)

}
