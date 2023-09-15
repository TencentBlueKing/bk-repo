/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.model.TFavorites
import com.tencent.bkrepo.repository.pojo.favorite.FavoriteCreateRequset
import com.tencent.bkrepo.repository.pojo.favorite.FavoritePageRequest
import com.tencent.bkrepo.repository.pojo.favorite.FavoriteProjectPageRequest
import com.tencent.bkrepo.repository.pojo.favorite.FavoriteRequest
import com.tencent.bkrepo.repository.service.favorites.FavoriteService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Api("用户收藏接口")
@RestController
@RequestMapping("/api/favorite")
class FavoriteController(
    private val favoriteService: FavoriteService,
    private val permissionManager: PermissionManager
) {

    @ApiOperation("创建收藏文件夹")
    @PostMapping( "/create")
    fun mkFavorite(
        @RequestAttribute userId: String,
        @RequestBody favoriteRequest: FavoriteRequest
    ): Response<Void> {
        with(favoriteRequest) {
            permissionManager.checkNodePermission(PermissionAction.VIEW, projectId, repoName, path)
            val createRequest = FavoriteCreateRequset(
                projectId = projectId,
                repoName = repoName,
                path = path,
                createdDate = LocalDateTime.now(),
                userId = userId
            )
            favoriteService.createFavorite(createRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("删除收藏")
    @DeleteMapping("/delete/{id}")
    fun removeFavorite(
        @RequestAttribute userId: String,
        @PathVariable id:String
    ): Response<Void> {
        favoriteService.getFavoriteById(id)?.let {
            if(it.type.equals(ResourceType.PROJECT.name)){
                permissionManager.checkProjectPermission(PermissionAction.VIEW, it.projectId)
            } else if (it.type.equals(ResourceType.REPO.name)) {
                permissionManager.checkRepoPermission(PermissionAction.VIEW, it.projectId, it.repoName?: "")
            } else {
                permissionManager.checkNodePermission(PermissionAction.VIEW, it.projectId, it.repoName?: "", it.path?: "")
            }
            favoriteService.removeFavorite(id)
            return ResponseBuilder.success()
        }
        return ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, "id not existed")
    }

    @ApiOperation("收藏文件夹分页查询")
    @PostMapping("/page")
    fun pageFavorite(
        @RequestBody favoritePageRequest: FavoritePageRequest
    ): Response<Page<TFavorites>> {
        return ResponseBuilder.success(favoriteService.pageFavorite(favoritePageRequest))
    }

    @ApiOperation("创建项目收藏")
    @PostMapping( "/create/project/{projectId}")
    fun mkProjectFavorite(
        @RequestAttribute userId: String,
        @PathVariable projectId: String
    ): Response<Void> {
        with(projectId) {
            permissionManager.checkProjectPermission(PermissionAction.VIEW, projectId)
            val createRequest = FavoriteCreateRequset(
                projectId = projectId,
                repoName = null,
                path = null,
                createdDate = LocalDateTime.now(),
                userId = userId,
                type = ResourceType.PROJECT.name
            )
            favoriteService.createFavorite(createRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("收藏文件夹分页查询")
    @PostMapping("/page/project")
    fun pageProjectFavorite(
        @RequestBody favoriteProjectPageRequest: FavoriteProjectPageRequest
    ): Response<Page<TFavorites>> {
        return ResponseBuilder.success(favoriteService.pageProjectFavorite(favoriteProjectPageRequest))
    }
}