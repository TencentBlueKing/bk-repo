/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.auth.condition.BkV3RbacAuthCondition
import com.tencent.bkrepo.auth.condition.DevopsAuthCondition
import com.tencent.bkrepo.auth.condition.LocalAuthCondition
import com.tencent.bkrepo.auth.dao.AccountDao
import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.PersonalPathDao
import com.tencent.bkrepo.auth.dao.RepoAuthConfigDao
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.dao.repository.OauthTokenRepository
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.auth.service.bkdevops.DevopsPermissionServiceImpl
import com.tencent.bkrepo.auth.service.bkdevops.DevopsPipelineService
import com.tencent.bkrepo.auth.service.bkdevops.DevopsProjectService
import com.tencent.bkrepo.auth.service.bkiamv3.BkIamV3PermissionServiceImpl
import com.tencent.bkrepo.auth.service.bkiamv3.BkIamV3Service
import com.tencent.bkrepo.auth.service.local.AccountServiceImpl
import com.tencent.bkrepo.auth.service.local.PermissionServiceImpl
import com.tencent.bkrepo.auth.service.local.RoleServiceImpl
import com.tencent.bkrepo.auth.service.local.UserServiceImpl
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.core.Ordered

@Configuration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
class AuthServiceConfig {

    @Autowired
    @Lazy
    private lateinit var repositoryService: RepositoryService

    @Autowired
    @Lazy
    private lateinit var projectService: ProjectService

    @Bean
    @ConditionalOnMissingBean(AccountService::class)
    fun accountService(
        accountDao: AccountDao,
        oauthTokenRepository: OauthTokenRepository,
        userDao: UserDao
    ) = AccountServiceImpl(accountDao, oauthTokenRepository, userDao)

    @Bean
    @Conditional(LocalAuthCondition::class)
    fun permissionService(
        roleRepository: RoleRepository,
        accountDao: AccountDao,
        permissionDao: PermissionDao,
        userDao: UserDao,
        personalPathDao: PersonalPathDao,
        repoAuthConfigDao: RepoAuthConfigDao
    ): PermissionService {
        return PermissionServiceImpl(
            roleRepository,
            accountDao,
            permissionDao,
            userDao,
            personalPathDao,
            repoAuthConfigDao,
            repositoryService,
            projectService
        )
    }

    @Conditional(BkV3RbacAuthCondition::class)
    fun bkiamV3PermissionService(
        bkiamV3Service: BkIamV3Service,
        userDao: UserDao,
        personalPathDao: PersonalPathDao,
        repoAuthConfigDao: RepoAuthConfigDao,
        roleRepository: RoleRepository,
        accountDao: AccountDao,
        permissionDao: PermissionDao,
    ): PermissionService {
        return BkIamV3PermissionServiceImpl(
            bkiamV3Service,
            userDao,
            roleRepository,
            accountDao,
            permissionDao,
            personalPathDao,
            repoAuthConfigDao,
            repositoryService,
            projectService
        )
    }

    @Bean
    @Conditional(DevopsAuthCondition::class)
    fun bkAuthPermissionService(
        roleRepository: RoleRepository,
        accountDao: AccountDao,
        permissionDao: PermissionDao,
        userDao: UserDao,
        personalPathDao: PersonalPathDao,
        repoAuthConfigDao: RepoAuthConfigDao,
        bkAuthConfig: DevopsAuthConfig,
        bkAuthPipelineService: DevopsPipelineService,
        bkAuthProjectService: DevopsProjectService,
        bkiamV3Service: BkIamV3Service
    ): PermissionService {
        return DevopsPermissionServiceImpl(
            roleRepository,
            accountDao,
            permissionDao,
            userDao,
            personalPathDao,
            repoAuthConfigDao,
            bkAuthConfig,
            bkAuthPipelineService,
            bkAuthProjectService,
            repositoryService,
            projectService,
            bkiamV3Service
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
