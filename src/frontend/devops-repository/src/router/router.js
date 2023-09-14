const repoPreview = () => import('@repository/views/preview')
const scanTask = () => import('@repository/views/preview/scanTask')

const repoHome = () => import('@repository/views')
const repoList = () => import('@repository/views/repoList')
const repoConfig = () => import('@repository/views/repoConfig')
const repoToken = () => import('@repository/views/repoToken')
const userCenter = () => import('@repository/views/userCenter')
const userManage = () => import('@repository/views/userManage')
const repoAudit = () => import('@repository/views/repoAudit')
const projectManage = () => import('@repository/views/projectManage')
const projectConfig = () => import('@repository/views/projectManage/projectConfig')
const nodeManage = () => import('@repository/views/nodeManage')
const planManage = () => import('@repository/views/planManage')
const logDetail = () => import('@repository/views/planManage/logDetail')

const repoScan = () => import('@repository/views/repoScan')
const scanReport = () => import('@repository/views/repoScan/scanReport')
const artiReport = () => import('@repository/views/repoScan/artiReport')
const scanConfig = () => import('@repository/views/repoScan/scanConfig')
const startScan = () => import('@repository/views/repoScan/startScan')

const securityConfig = () => import('@repository/views/repoScan/securityConfig')

const repoGeneric = () => import('@repository/views/repoGeneric')

const commonPackageList = () => import('@repository/views/repoCommon/commonPackageList')
const commonPackageDetail = () => import('@repository/views/repoCommon/commonPackageDetail')

const repoSearch = () => import('@repository/views/repoSearch')

const WebError440 = () => import('@repository/components/Exception/440')

const oauth = () => import('@repository/views/oauth')

const routes = [
    {
        path: '/ui/:projectId/preview',
        component: repoPreview,
        children: [
            {
                path: 'scanTask/:planId/:taskId',
                component: scanTask
            }
        ]
    },
    {
        path: '/ui/:projectId/oauth/authorize',
        component: oauth
    },
    {
        path: '/ui/:projectId',
        component: repoHome,
        redirect: { name: 'repositories' },
        children: [
            {
                path: 'repoList',
                name: 'repositories',
                component: repoList,
                meta: {
                    breadcrumb: [
                        { to: 'repositories', name: 'browser', label: '仓库列表' }
                    ]
                }
            },
            {
                path: '440/:msg',
                name: '440',
                component: WebError440
            },
            {
                path: 'repoConfig/:repoType',
                name: 'repoConfig',
                component: repoConfig,
                meta: {
                    breadcrumb: [
                        { to: 'repositories', name: 'browser', label: '仓库列表' },
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
                        { name: 'repoToken', label: '个人令牌' }
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
                path: 'planManage/logDetail/:logId',
                name: 'logDetail',
                component: logDetail,
                meta: {
                    breadcrumb: [
                        { name: 'planManage', label: '{planName}', template: 'planManage' },
                        { name: 'logDetail', label: '日志详情' }
                    ]
                }
            },
            {
                path: 'repoScan',
                name: 'repoScan',
                component: repoScan,
                meta: {
                    breadcrumb: [
                        { name: 'repoScan', label: '制品分析' }
                    ]
                }
            },
            {
                path: 'scanReport/:planId',
                name: 'scanReport',
                component: scanReport,
                meta: {
                    breadcrumb: [
                        { name: 'repoScan', label: '制品分析' },
                        { name: 'scanReport', label: '{scanName}', template: 'scanReport' }
                    ]
                }
            },
            {
                path: 'artiReport/:planId/:recordId',
                name: 'artiReport',
                component: artiReport,
                beforeEnter: (to, from, next) => {
                    const repoType = to.query.repoType
                    if (to.query.scanName) {
                        to.meta.breadcrumb = [
                            { name: 'repoScan', label: '制品分析' },
                            { name: 'scanReport', label: '{scanName}', template: 'scanReport' },
                            { name: 'artiReport', label: '{artiName}', template: 'artiReport' }
                        ]
                    } else if (repoType === 'generic') {
                        to.meta.breadcrumb = [
                            { to: 'repositories', name: 'browser', label: '仓库列表' },
                            { name: 'repoGeneric', label: '{repoName}', template: 'repoGeneric' },
                            { name: 'artiReport', label: '制品扫描结果' }
                        ]
                    } else if (repoType) {
                        to.meta.breadcrumb = [
                            { to: 'repositories', name: 'browser', label: '仓库列表' },
                            { name: 'commonList', label: '{repoName}', template: 'commonList' },
                            { name: 'commonPackage', label: '{packageKey}', template: 'commonPackage' },
                            { name: 'artiReport', label: '制品扫描结果' }
                        ]
                    }
                    next()
                }
            },
            {
                path: 'scanConfig/:planId',
                name: 'scanConfig',
                component: scanConfig,
                meta: {
                    breadcrumb: [
                        { name: 'repoScan', label: '制品分析' },
                        { name: 'scanConfig', label: '{scanName}', template: 'scanConfig' }
                    ]
                }
            },
            {
                path: 'startScan/:planId',
                name: 'startScan',
                component: startScan,
                meta: {
                    breadcrumb: [
                        { name: 'repoScan', label: '制品分析' },
                        { name: 'scanReport', label: '{scanName}', template: 'scanReport' },
                        { name: 'startScan', label: '立即扫描' }
                    ]
                }
            },
            {
                path: 'securityConfig',
                name: 'securityConfig',
                component: securityConfig,
                meta: {
                    breadcrumb: [
                        { name: 'securityConfig', label: '安全设置' }
                    ]
                }
            },
            {
                path: 'generic',
                name: 'repoGeneric',
                component: repoGeneric,
                meta: {
                    breadcrumb: [
                        { to: 'repositories', name: 'browser', label: '仓库列表' },
                        { name: 'repoGeneric', label: '{repoName}', template: 'repoGeneric' }
                    ]
                }
            },
            {
                path: ':repoType/list',
                name: 'commonList',
                component: commonPackageList,
                meta: {
                    breadcrumb: [
                        { to: 'repositories', name: 'browser', label: '仓库列表' },
                        { name: 'commonList', label: '{repoName}', template: 'commonList' }
                    ]
                }
            },
            {
                path: ':repoType/package',
                name: 'commonPackage',
                component: commonPackageDetail,
                meta: {
                    breadcrumb: [
                        { to: 'repositories', name: 'browser', label: '仓库列表' },
                        { name: 'commonList', label: '{repoName}', template: 'commonList' },
                        { name: 'commonPackage', label: '{packageKey}', template: 'commonPackage' }
                    ]
                }
            }
        ]
    }
]

export default routes
