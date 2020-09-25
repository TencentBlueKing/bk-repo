/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PrepoCULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

const repoHome = () => import(/* webpackChunkName: "repoHome" */'@/views')

const repoList = () => import(/* webpackChunkName: "repoList" */'@/views/repoList')
const createRepo = () => import(/* webpackChunkName: "createRepo" */'@/views/repoList/createRepo')
const repoConfig = () => import(/* webpackChunkName: "repoConfig" */'@/views/repoConfig')
const repoDetail = () => import(/* webpackChunkName: "repoDetail" */'@/views/repo')

const repoGeneric = () => import(/* webpackChunkName: "repoGeneric" */'@/views/repo/repoGeneric')

const repoDocker = () => import(/* webpackChunkName: "repoDocker" */'@/views/repo/repoDocker')

const repoNpm = () => import(/* webpackChunkName: "repoNpm" */'@/views/repo/repoNpm')

const repoSearch = () => import(/* webpackChunkName: "repoSearch" */'@/views/repoSearch')

const routes = [
    {
        path: '/ui/:projectId',
        component: repoHome,
        children: [
            {
                path: '',
                redirect: {
                    name: 'repoList'
                }
            },
            {
                path: 'repoList',
                name: 'repoList',
                component: repoList,
                meta: {
                    title: '仓库列表',
                    header: '仓库列表',
                    icon: 'repo',
                    to: 'repoList'
                }
            },
            {
                path: 'createRepo',
                name: 'createRepo',
                component: createRepo,
                meta: {
                    title: '',
                    header: '新建仓库',
                    icon: 'repo',
                    to: 'repoList'
                }
            },
            {
                path: 'repoConfig/:type',
                name: 'repoConfig',
                component: repoConfig,
                meta: {
                    title: '仓库配置',
                    header: '仓库配置',
                    icon: 'repo',
                    to: 'repoList'
                }
            },
            {
                path: 'repoDetail',
                name: 'repoDetail',
                component: repoDetail,
                meta: {
                    title: '仓库主页',
                    header: '仓库主页',
                    icon: 'repo',
                    to: 'repoList'
                },
                children: [
                    {
                        path: 'generic',
                        name: 'generic',
                        component: repoGeneric
                    },
                    {
                        path: 'docker',
                        name: 'docker',
                        component: repoDocker
                    },
                    {
                        path: 'npm',
                        name: 'npm',
                        component: repoNpm
                    }
                ]
            },
            {
                path: 'repoSearch',
                name: 'repoSearch',
                component: repoSearch,
                meta: {
                    title: '文件搜索',
                    header: '文件搜索',
                    icon: 'repo',
                    to: 'repoList'
                }
            }
        ]
    }
]

export default routes
