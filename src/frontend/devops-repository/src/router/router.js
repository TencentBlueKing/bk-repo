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
const createPlan = () => import('@repository/views/planManage/createPlan')
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
                        { name: 'repoList', label: '????????????' }
                    ]
                }
            },
            {
                path: 'repoConfig/:repoType',
                name: 'repoConfig',
                component: repoConfig,
                meta: {
                    breadcrumb: [
                        { name: 'repoList', label: '????????????' },
                        { name: 'repoConfig', label: '????????????' }
                    ]
                }
            },
            {
                path: 'repoSearch',
                name: 'repoSearch',
                component: repoSearch,
                meta: {
                    breadcrumb: [
                        { name: 'repoSearch', label: '????????????' }
                    ]
                }
            },
            {
                path: 'projectManage',
                name: 'projectManage',
                component: projectManage,
                meta: {
                    breadcrumb: [
                        { name: 'projectManage', label: '????????????' }
                    ]
                }
            },
            {
                path: 'projectConfig',
                name: 'projectConfig',
                component: projectConfig,
                meta: {
                    breadcrumb: [
                        { name: 'projectConfig', label: '????????????' }
                    ]
                }
            },
            {
                path: 'repoToken',
                name: 'repoToken',
                component: repoToken,
                meta: {
                    breadcrumb: [
                        { name: 'repoToken', label: '????????????' }
                    ]
                }
            },
            {
                path: 'userCenter',
                name: 'userCenter',
                component: userCenter,
                meta: {
                    breadcrumb: [
                        { name: 'userCenter', label: '????????????' }
                    ]
                }
            },
            {
                path: 'userManage',
                name: 'userManage',
                component: userManage,
                meta: {
                    breadcrumb: [
                        { name: 'userManage', label: '????????????' }
                    ]
                }
            },
            {
                path: 'repoAudit',
                name: 'repoAudit',
                component: repoAudit,
                meta: {
                    breadcrumb: [
                        { name: 'repoAudit', label: '????????????' }
                    ]
                }
            },
            {
                path: 'nodeManage',
                name: 'nodeManage',
                component: nodeManage,
                meta: {
                    breadcrumb: [
                        { name: 'nodeManage', label: '????????????' }
                    ]
                }
            },
            {
                path: 'planManage',
                name: 'planManage',
                component: planManage,
                meta: {
                    breadcrumb: [
                        { name: 'planManage', label: '????????????' }
                    ]
                }
            },
            {
                path: 'planManage/createPlan',
                name: 'createPlan',
                component: createPlan,
                meta: {
                    breadcrumb: [
                        { name: 'planManage', label: '????????????' },
                        { name: 'createPlan', label: '????????????' }
                    ]
                }
            },
            {
                path: 'planManage/editPlan/:planId',
                name: 'editPlan',
                component: createPlan,
                meta: {
                    breadcrumb: [
                        { name: 'planManage', label: '{planName}', template: '????????????' },
                        { name: 'createPlan', label: '????????????' }
                    ]
                }
            },
            {
                path: 'planManage/planDetail/:planId',
                name: 'planDetail',
                component: createPlan,
                meta: {
                    breadcrumb: [
                        { name: 'planManage', label: '{planName}', template: '????????????' },
                        { name: 'createPlan', label: '????????????' }
                    ]
                }
            },
            {
                path: 'planManage/logDetail/:logId',
                name: 'logDetail',
                component: logDetail,
                meta: {
                    breadcrumb: [
                        { name: 'planManage', label: '{planName}', template: '????????????' },
                        { name: 'logDetail', label: '????????????' }
                    ]
                }
            },
            {
                path: 'repoScan',
                name: 'repoScan',
                component: repoScan,
                meta: {
                    breadcrumb: [
                        { name: 'repoScan', label: '????????????' }
                    ]
                }
            },
            {
                path: 'scanReport/:planId',
                name: 'scanReport',
                component: scanReport,
                meta: {
                    breadcrumb: [
                        { name: 'repoScan', label: '????????????' },
                        { name: 'scanReport', label: '{scanName}', template: '????????????' }
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
                            { name: 'repoScan', label: '????????????' },
                            { name: 'scanReport', label: '{scanName}', template: '????????????' },
                            { name: 'artiReport', label: '{artiName}', template: '??????????????????' }
                        ]
                    } else if (repoType === 'generic') {
                        to.meta.breadcrumb = [
                            { name: 'repoList', label: '????????????' },
                            { name: 'repoGeneric', label: '{repoName}', template: '???????????????' },
                            { name: 'artiReport', label: '??????????????????' }
                        ]
                    } else if (repoType) {
                        to.meta.breadcrumb = [
                            { name: 'repoList', label: '????????????' },
                            { name: 'commonList', label: '{repoName}', template: '????????????' },
                            { name: 'commonPackage', label: '{packageKey}', template: '????????????' },
                            { name: 'artiReport', label: '??????????????????' }
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
                        { name: 'repoScan', label: '????????????' },
                        { name: 'scanConfig', label: '{scanName}', template: '????????????' }
                    ]
                }
            },
            {
                path: 'startScan/:planId',
                name: 'startScan',
                component: startScan,
                meta: {
                    breadcrumb: [
                        { name: 'repoScan', label: '????????????' },
                        { name: 'scanReport', label: '{scanName}', template: '????????????' },
                        { name: 'startScan', label: '????????????' }
                    ]
                }
            },
            {
                path: 'securityConfig',
                name: 'securityConfig',
                component: securityConfig,
                meta: {
                    breadcrumb: [
                        { name: 'securityConfig', label: '????????????' }
                    ]
                }
            },
            {
                path: 'generic',
                name: 'repoGeneric',
                component: repoGeneric,
                meta: {
                    breadcrumb: [
                        { name: 'repoList', label: '????????????' },
                        { name: 'repoGeneric', label: '{repoName}', template: '???????????????' }
                    ]
                }
            },
            {
                path: ':repoType/list',
                name: 'commonList',
                component: commonPackageList,
                meta: {
                    breadcrumb: [
                        { name: 'repoList', label: '????????????' },
                        { name: 'commonList', label: '{repoName}', template: '????????????' }
                    ]
                }
            },
            {
                path: ':repoType/package',
                name: 'commonPackage',
                component: commonPackageDetail,
                meta: {
                    breadcrumb: [
                        { name: 'repoList', label: '????????????' },
                        { name: 'commonList', label: '{repoName}', template: '????????????' },
                        { name: 'commonPackage', label: '{packageKey}', template: '????????????' }
                    ]
                }
            }
        ]
    }
]

export default routes
