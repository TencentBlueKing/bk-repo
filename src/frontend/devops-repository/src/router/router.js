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
const repoConfig = () => import(/* webpackChunkName: "repoConfig" */'@/views/repoConfig')
const repoToken = () => import(/* webpackChunkName: "repoToken" */'@/views/repoToken')
const userCenter = () => import(/* webpackChunkName: "userCenter" */'@/views/userCenter')
const userManage = () => import(/* webpackChunkName: "userManage" */'@/views/userManage')
const repoAudit = () => import(/* webpackChunkName: "repoAudit" */'@/views/repoAudit')
const projectManage = () => import(/* webpackChunkName: "projectManage" */'@/views/projectManage')
const projectConfig = () => import(/* webpackChunkName: "projectManage" */'@/views/projectManage/projectConfig')
const nodeManage = () => import(/* webpackChunkName: "nodeManage" */'@/views/nodeManage')
const planManage = () => import(/* webpackChunkName: "planManage" */'@/views/planManage')
const createPlan = () => import(/* webpackChunkName: "createPlan" */'@/views/planManage/createPlan')
const logDetail = () => import(/* webpackChunkName: "logDetail" */'@/views/planManage/logDetail')

const repoGeneric = () => import(/* webpackChunkName: "repoGeneric" */'@/views/repoGeneric')

const commonPackageList = () => import(/* webpackChunkName: "repoCommon" */'@/views/repoCommon/commonPackageList')
const commonPackageDetail = () => import(/* webpackChunkName: "repoCommon" */'@/views/repoCommon/commonPackageDetail')

const repoSearch = () => import(/* webpackChunkName: "repoSearch" */'@/views/repoSearch')

const routes = [
    {
        path: '/ui/:projectId',
        component: repoHome,
        redirect: { name: 'repoList' },
        children: [
            {
                path: 'repoList',
                name: 'repoList',
                component: repoList,
                meta: {
                    breadcrumb: [
                        { name: 'repoList', label: '仓库列表' }
                    ]
                }
            },
            {
                path: 'repoConfig/:repoType',
                name: 'repoConfig',
                component: repoConfig,
                meta: {
                    breadcrumb: [
                        { name: 'repoList', label: '仓库列表' },
                        { name: 'repoConfig', label: '仓库配置' }
                    ]
                }
            },
            {
                path: 'repoSearch',
                name: 'repoSearch',
                component: repoSearch,
                meta: {
                    breadcrumb: [
                        { name: 'repoSearch', label: '制品搜索' }
                    ]
                }
            },
            {
                path: 'projectManage',
                name: 'projectManage',
                component: projectManage,
                meta: {
                    breadcrumb: [
                        { name: 'projectManage', label: '项目管理' }
                    ]
                }
            },
            {
                path: 'projectConfig',
                name: 'projectConfig',
                component: projectConfig,
                meta: {
                    breadcrumb: [
                        { name: 'projectConfig', label: '项目设置' }
                    ]
                }
            },
            {
                path: 'repoToken',
                name: 'repoToken',
                component: repoToken,
                meta: {
                    breadcrumb: [
                        { name: 'repoToken', label: '访问令牌' }
                    ]
                }
            },
            {
                path: 'userCenter',
                name: 'userCenter',
                component: userCenter,
                meta: {
                    breadcrumb: [
                        { name: 'userCenter', label: '个人中心' }
                    ]
                }
            },
            {
                path: 'userManage',
                name: 'userManage',
                component: userManage,
                meta: {
                    breadcrumb: [
                        { name: 'userManage', label: '用户管理' }
                    ]
                }
            },
            {
                path: 'repoAudit',
                name: 'repoAudit',
                component: repoAudit,
                meta: {
                    breadcrumb: [
                        { name: 'repoAudit', label: '审计日志' }
                    ]
                }
            },
            {
                path: 'nodeManage',
                name: 'nodeManage',
                component: nodeManage,
                meta: {
                    breadcrumb: [
                        { name: 'nodeManage', label: '节点管理' }
                    ]
                }
            },
            {
                path: 'planManage',
                name: 'planManage',
                component: planManage,
                meta: {
                    breadcrumb: [
                        { name: 'planManage', label: '制品分发' }
                    ]
                }
            },
            {
                path: 'planManage/createPlan',
                name: 'createPlan',
                component: createPlan,
                meta: {
                    breadcrumb: [
                        { name: 'planManage', label: '制品分发' },
                        { name: 'createPlan', label: '创建计划' }
                    ]
                }
            },
            {
                path: 'planManage/editPlan/:planId',
                name: 'editPlan',
                component: createPlan,
                meta: {
                    breadcrumb: [
                        { name: 'planManage', label: '{planName}', template: '制品分发' },
                        { name: 'createPlan', label: '编辑计划' }
                    ]
                }
            },
            {
                path: 'planManage/planDetail/:planId',
                name: 'planDetail',
                component: createPlan,
                meta: {
                    breadcrumb: [
                        { name: 'planManage', label: '{planName}', template: '制品分发' },
                        { name: 'createPlan', label: '计划详情' }
                    ]
                }
            },
            {
                path: 'planManage/logDetail/:logId',
                name: 'logDetail',
                component: logDetail,
                meta: {
                    breadcrumb: [
                        { name: 'planManage', label: '{planName}', template: '制品分发' },
                        { name: 'logDetail', label: '日志详情' }
                    ]
                }
            },
            {
                path: 'generic',
                name: 'repoGeneric',
                component: repoGeneric,
                meta: {
                    breadcrumb: [
                        { name: 'repoList', label: '仓库列表' },
                        { name: 'repoGeneric', label: '{repoName}', template: '二进制仓库' }
                    ]
                }
            },
            {
                path: ':repoType/list',
                name: 'commonList',
                component: commonPackageList,
                meta: {
                    breadcrumb: [
                        { name: 'repoList', label: '仓库列表' },
                        { name: 'commonList', label: '{repoName}', template: '依赖仓库' }
                    ]
                }
            },
            {
                path: ':repoType/package',
                name: 'commonPackage',
                component: commonPackageDetail,
                meta: {
                    breadcrumb: [
                        { name: 'repoList', label: '仓库列表' },
                        { name: 'commonList', label: '{repoName}', template: '依赖仓库' },
                        { name: 'commonPackage', label: '{package}', template: '制品详情' }
                    ]
                }
            }
        ]
    }
]

export default routes
