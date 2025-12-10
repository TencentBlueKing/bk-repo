import Vue from 'vue'
import Router from 'vue-router'
export const TITLE_HOME = sidebarTitle
export const ROUTER_NAME_ACCOUNT = 'Account'
export const ROUTER_NAME_SERVICE = 'Service'
export const ROUTER_NAME_SERVICE_CONFIG = 'ServiceConfig'
export const ROUTER_NAME_NODE = 'Node'
export const ROUTER_NAME_EMPTY_FOLDER = 'EmptyFolder'
export const ROUTER_NAME_FIRST_LEVEL_FOLDER = 'FirstLevelFolder'
export const ROUTER_NAME_INSTANCE = 'Instance'
export const ROUTER_NAME_STORAGE_CREDENTIALS = 'StorageCredentials'
export const ROUTER_NAME_STORAGE_METRICS = 'StorageMetrics'
export const ROUTER_NAME_STORAGE_METRIC_DETAIL = 'StorageMetricDetail'
export const ROUTER_NAME_EXT_PERMISSION = 'ExtPermission'
export const ROUTER_NAME_WEBHOOK = 'Webhook'
export const ROUTER_NAME_NOTIFY_CREDENTIALS = 'NotifyCredentials'
export const ROUTER_NAME_PLUGIN = 'Plugin'
export const ROUTER_NAME_SCANNERS = 'Scanners'
export const ROUTER_NAME_PROJECT_SCAN_CONFIGURATIONS = 'ProjectScanConfigurations'
export const ROUTER_NAME_FILTER_RULE = 'FilterRule'
export const ROUTER_NAME_JOB = 'Job'
export const ROUTER_NAME_SHED_LOCK = 'Shedlock'
export const ROUTER_NAME_PROJECT_METRICS = 'ProjectMetrics'
export const ROUTER_NAME_FILE_SYSTEM = 'FileSystem'
export const ROUTER_NAME_FILE_CACHE = 'FileCache'
export const ROUTER_NAME_FILE_SYSTEM_RECORD = 'FileSystemRecord'
export const ROUTER_NAME_REPO_CONFIG = 'RepoConfig'
export const ROUTER_NAME_RATE_LIMITER_CONFIG = 'RateLimiterConfig'
export const ROUTER_NAME_PRELOAD_CONFIG = 'PreloadConfig'
export const ROUTER_NAME_EXECUTION_CLUSTERS_CONFIG = 'ExecutionClustersConfig'
export const ROUTER_NAME_SEPARATION_CONFIG = 'SeparationConfig'
export const ROUTER_NAME_SEPARATION_RECORD = 'SeparationRecord'
export const ROUTER_NAME_MIGRATE_REPO_STORAGE_CONFIG = 'MigrationConfig'
export const ROUTER_NAME_MIGRATE_PROJECT_GRAYSCALE_CONFIG = 'ProjectGrayscaleConfig'
export const ROUTER_NAME_SERVER_LOG = 'ServerLog'
export const ROUTER_NAME_SIGN_CONFIG = 'SignConfig'

Vue.use(Router)

/* Layout */
import Layout from '@/layout'
import { sidebarTitle } from '@/settings'

/**
 * Note: sub-menu only appear when route children.length >= 1
 * Detail see: https://panjiachen.github.io/vue-element-admin-site/guide/essentials/router-and-nav.html
 *
 * hidden: true                   if set true, item will not show in the sidebar(default is false)
 * alwaysShow: true               if set true, will always show the root menu
 *                                if not set alwaysShow, when item has more than one children route,
 *                                it will becomes nested mode, otherwise not show the root menu
 * redirect: noRedirect           if set noRedirect will no redirect in the breadcrumb
 * name:'router-name'             the name is used by <keep-alive> (must set!!!)
 * meta : {
    roles: ['admin','editor']    control the page roles (you can set multiple roles)
    title: 'title'               the name show in sidebar and breadcrumb (recommend set)
    icon: 'svg-name'/'el-icon-x' the icon show in the sidebar
    breadcrumb: false            if set false, the item will hidden in breadcrumb(default is true)
    activeMenu: '/example/list'  if set path, the sidebar will highlight the path you set
  }
 */

/**
 * constantRoutes
 * a base page that does not have permission requirements
 * all roles can be accessed
 */
export const constantRoutes = [
  {
    path: '/login',
    component: () => import('@/views/login/index'),
    hidden: true
  },

  {
    path: '/',
    redirect: process.env.VUE_APP_RELEASE_MODE === 'community' ? '/ops/nodes' : '/ops/services',
    meta: { title: TITLE_HOME, icon: 'bk' }
  },

  {
    path: '/404',
    component: () => import('@/views/404'),
    hidden: true
  }
]

/**
 * asyncRoutes
 * the routes that need to be dynamically loaded based on user roles
 */
export const asyncRoutes = [
  {
    path: '/core',
    alwaysShow: true,
    redirect: '/core/account',
    component: Layout,
    meta: { title: '核心服务', icon: 'service' },
    children: [
      {
        path: 'account', // 改为相对路径
        name: ROUTER_NAME_ACCOUNT,
        meta: { title: '平台账户管理', icon: 'user' },
        component: () => import('@/views/account/index')
      }
    ]
  },
  {
    path: '/ops',
    alwaysShow: true,
    component: Layout,
    redirect: '/ops/services',
    meta: { title: '开发与运维', icon: 'service' },
    children: [
      {
        path: 'services',
        name: 'ServiceMenu',
        meta: { title: '服务管理', icon: 'service' },
        hidden: process.env.VUE_APP_RELEASE_MODE === 'community',
        component: () => import('@/views/second'),
        redirect: '/ops/services/list',
        children: [
          {
            path: 'list',
            name: ROUTER_NAME_SERVICE,
            meta: { title: '服务管理', icon: 'service' },
            component: () => import('@/views/service/index')
          },
          {
            path: 'configs',
            name: ROUTER_NAME_SERVICE_CONFIG,
            meta: { title: '配置管理', icon: 'setting' },
            component: () => import('@/views/serviceConfig/index')
          },
          {
            path: ':serviceName/instances',
            name: ROUTER_NAME_INSTANCE,
            hidden: true,
            component: () => import('@/views/service/Instance'),
            props: true,
            meta: { title: '服务实例' }
          }
        ]
      },
      {
        path: 'nodes',
        component: () => import('@/views/second'),
        meta: { title: '文件管理', icon: 'file' },
        redirect: '/ops/nodes/nodes',
        children: [
          {
            path: 'nodes',
            name: ROUTER_NAME_NODE,
            meta: { title: '文件管理', icon: 'file' },
            component: () => import('@/views/node/index')
          },
          {
            path: 'fileSystem',
            name: ROUTER_NAME_FILE_SYSTEM,
            meta: { title: '客户端管理', icon: 'file' },
            component: () => import('@/views/node/FileSystem')
          },
          {
            path: 'fileSystemRecord',
            name: ROUTER_NAME_FILE_SYSTEM_RECORD,
            meta: { title: '客户端统计', icon: 'file' },
            component: () => import('@/views/node/FileSystemRecord')
          },
          {
            path: 'emptyFolder',
            name: ROUTER_NAME_EMPTY_FOLDER,
            meta: { title: '清理空目录', icon: 'file' },
            component: () => import('@/views/node/EmptyFolder')
          },
          {
            path: 'firstLevelFolder',
            name: ROUTER_NAME_FIRST_LEVEL_FOLDER,
            meta: { title: '一级目录统计', icon: 'file' },
            component: () => import('@/views/node/FirstLevelFolder')
          },
          {
            path: 'projectMetrics',
            name: ROUTER_NAME_PROJECT_METRICS,
            meta: { title: '仓库大小统计', icon: 'file' },
            component: () => import('@/views/node/ProjectMetrics')
          }
        ]
      },
      {
        path: 'fileCache',
        component: () => import('@/views/second'),
        meta: { title: '缓存管理', icon: 'file' },
        redirect: '/ops/fileCache/fileCacheManage',
        children: [
          {
            path: 'fileCacheManage',
            name: ROUTER_NAME_FILE_CACHE,
            meta: { title: '缓存管理', icon: 'file' },
            component: () => import('@/views/node/FileCache')
          },
          {
            path: 'preloadConfig',
            name: ROUTER_NAME_PRELOAD_CONFIG,
            meta: { title: '制品预加载配置', icon: 'service-config' },
            component: () => import('@/views/preload/index')
          }
        ]
      },
      {
        path: 'storage',
        alwaysShow: true,
        component: () => import('@/views/second'),
        meta: { title: '存储管理', icon: 'storage' },
        redirect: '/ops/storage/credentials',
        children: [
          {
            path: 'credentials',
            name: ROUTER_NAME_STORAGE_CREDENTIALS,
            component: () => import('@/views/storage/Credential'),
            meta: { title: '凭据', icon: 'credentials' }
          },
          {
            path: 'metrics',
            name: ROUTER_NAME_STORAGE_METRICS,
            component: () => import('@/views/storage/Metrics'),
            meta: { title: '挂载存储节点统计', icon: 'credentials' }
          },
          {
            path: 'metricsDetail',
            name: ROUTER_NAME_STORAGE_METRIC_DETAIL,
            component: () => import('@/views/storage/MetricDetail'),
            meta: { title: '挂载存储节点详情', icon: 'credentials' }
          }
        ]
      },
      {
        path: 'shed-lock',
        name: ROUTER_NAME_SHED_LOCK,
        meta: { title: '数据库锁管理', icon: 'lock' },
        component: () => import('@/views/shed-lock/index')
      },
      {
        path: 'job',
        meta: { title: '任务管理', icon: 'cc-process' },
        redirect: '/ops/job/jobs',
        component: () => import('@/views/second'),
        children: [
          {
            path: 'jobs',
            name: ROUTER_NAME_JOB,
            meta: { title: '任务管理', icon: 'cc-process' },
            component: () => import('@/views/job/index')
          },
          {
            path: 'separate-task',
            name: ROUTER_NAME_SEPARATION_CONFIG,
            meta: { title: '降冷任务配置', icon: 'separate' },
            component: () => import('@/views/separation/index')
          },
          {
            path: 'separate-infos',
            name: ROUTER_NAME_SEPARATION_RECORD,
            meta: { title: '将冷任务数据查询', icon: 'separate' },
            component: () => import('@/views/separation/ShowData')
          },
          {
            path: 'migration-task',
            name: ROUTER_NAME_MIGRATE_REPO_STORAGE_CONFIG,
            meta: { title: '迁移任务管理', icon: 'separate' },
            component: () => import('@/views/migration/index')
          }
        ]
      },
      {
        path: 'rateLimiter',
        name: ROUTER_NAME_RATE_LIMITER_CONFIG,
        meta: { title: '限流管理', icon: 'permission' },
        component: () => import('@/views/rateLimitConfg/RateLimiter')
      }
    ]
  },
  {
    path: '/system',
    alwaysShow: true,
    component: Layout,
    meta: { title: '系统和安全', icon: 'service' },
    children: [
      {
        path: 'ext-permission',
        name: ROUTER_NAME_EXT_PERMISSION,
        meta: { title: '外部权限管理', icon: 'lock' },
        component: () => import('@/views/ext-permission/index')
      },
      {
        path: 'webhook',
        component: () => import('@/views/second'),
        redirect: '/system/webhook/list',
        hidden: process.env.VUE_APP_RELEASE_MODE === 'community',
        meta: { title: 'WebHook管理', icon: 'webhook' },
        children: [
          {
            path: 'list',
            name: ROUTER_NAME_WEBHOOK,
            meta: { title: 'WebHook', icon: 'webhook' },
            component: () => import('@/views/webhook/index')
          },
          {
            path: 'log',
            name: ROUTER_NAME_WEBHOOK,
            meta: { title: 'WebHook日志', icon: 'file' },
            component: () => import('@/views/webhook/log/index')
          }
        ]
      },
      {
        path: 'plugin',
        name: ROUTER_NAME_PLUGIN,
        meta: { title: '插件管理', icon: 'plugin' },
        component: () => import('@/views/plugin/index')
      },
      {
        path: 'repoConfig',
        name: ROUTER_NAME_REPO_CONFIG,
        meta: { title: 'REPO配置管理', icon: 'permission' },
        component: () => import('@/views/repoConfig/index')
      }
    ]
  },
  {
    path: '/support',
    alwaysShow: true,
    component: Layout,
    meta: { title: '辅助功能', icon: 'service' },
    redirect: '/support/grayscale-config',
    children: [
      {
        path: 'grayscale-config',
        name: ROUTER_NAME_MIGRATE_PROJECT_GRAYSCALE_CONFIG,
        meta: { title: '项目灰度管理', icon: 'permission' },
        component: () => import('@/views/grayscaleConfig/index')
      },
      {
        path: 'scan',
        hidden: process.env.VUE_APP_RELEASE_MODE === 'community',
        redirect: '/support/scan/scanners',
        component: () => import('@/views/second'),
        meta: { title: '制品分析管理', icon: 'scan' },
        children: [
          {
            path: 'scanners',
            name: ROUTER_NAME_SCANNERS,
            component: () => import('@/views/scan/Scanner'),
            meta: { title: '扫描器', icon: 'scanner' }
          },
          {
            path: 'rules',
            name: ROUTER_NAME_FILTER_RULE,
            component: () => import('@/views/scan/FilterRule.vue'),
            meta: { title: '过滤规则', icon: 'rule' }
          },
          {
            path: 'configurations',
            name: ROUTER_NAME_PROJECT_SCAN_CONFIGURATIONS,
            component: () => import('@/views/scan/ProjectScanConfiguration'),
            meta: { title: '项目配置', icon: 'setting' }
          },
          {
            path: 'executionClustersConfig',
            name: ROUTER_NAME_EXECUTION_CLUSTERS_CONFIG,
            meta: { title: '扫描执行集群配置', icon: 'service-config' },
            component: () => import('@/views/execution-clusters/index')
          }
        ]
      },
      {
        path: 'notify',
        redirect: '/support/notify/credentials',
        component: () => import('@/views/second'),
        meta: { title: '通知管理', icon: 'notify' },
        children: [
          {
            path: 'credentials',
            name: ROUTER_NAME_NOTIFY_CREDENTIALS,
            component: () => import('@/views/notify/Credential'),
            meta: { title: '凭据', icon: 'credentials' }
          }
        ]
      },
      {
        path: 'server-log',
        name: ROUTER_NAME_SERVER_LOG,
        meta: { title: '服务端日志', icon: 'server-log' },
        component: () => import('@/views/server-log/index')
      },
      {
        path: 'sign-config',
        name: ROUTER_NAME_SIGN_CONFIG,
        meta: { title: '水印加固配置管理', icon: 'lock' },
        component: () => import('@/views/sign-config/SignConfig')
      }
    ]
  },
  { path: '*', redirect: '/404', hidden: true }
]

const createRouter = () => new Router({
  mode: 'history', // require service support
  base: `/${process.env.VUE_APP_BASE_DIR}`,
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRoutes
})

const router = createRouter()

// Detail see: https://github.com/vuejs/vue-router/issues/1234#issuecomment-357941465
export function resetRouter() {
  const newRouter = createRouter()
  router.matcher = newRouter.matcher // reset router
}

export default router
