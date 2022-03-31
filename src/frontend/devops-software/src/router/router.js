const repoHome = () => import(/* webpackChunkName: "repoHome" */'@/views')

const repoList = () => import(/* webpackChunkName: "repoList" */'@/views/repoList')

const repoGeneric = () => import(/* webpackChunkName: "repoGeneric" */'@repository/views/repoGeneric')

const commonPackageList = () => import(/* webpackChunkName: "repoCommon" */'@repository/views/repoCommon/commonPackageList')
const commonPackageDetail = () => import(/* webpackChunkName: "repoCommon" */'@repository/views/repoCommon/commonPackageDetail')

const repoSearch = () => import(/* webpackChunkName: "repoSearch" */'@/views/repoSearch')

const userCenter = () => import(/* webpackChunkName: "userCenter" */'@repository/views/userCenter')
const repoToken = () => import(/* webpackChunkName: "repoToken" */'@repository/views/repoToken')

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
                        { name: 'commonPackage', label: '{package}', template: '制品详情' }
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
