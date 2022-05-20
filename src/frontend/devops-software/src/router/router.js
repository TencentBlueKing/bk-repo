const repoHome = () => import('@/views')

const repoList = () => import('@/views/repoList')

const repoGeneric = () => import('@repository/views/repoGeneric')

const commonPackageList = () => import('@repository/views/repoCommon/commonPackageList')
const commonPackageDetail = () => import('@repository/views/repoCommon/commonPackageDetail')

const repoSearch = () => import('@/views/repoSearch')

const userCenter = () => import('@repository/views/userCenter')
const repoToken = () => import('@repository/views/repoToken')

const routes = [
    {
        path: '/software',
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
                path: ':projectId/generic',
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
                path: ':projectId/:repoType/list',
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
                path: ':projectId/:repoType/package',
                name: 'commonPackage',
                component: commonPackageDetail,
                meta: {
                    breadcrumb: [
                        { name: 'repoList', label: '仓库列表' },
                        { name: 'commonList', label: '{repoName}', template: '依赖仓库' },
                        { name: 'commonPackage', label: '{packageKey}', template: '制品详情' }
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
            }
        ]
    }
]

export default routes
