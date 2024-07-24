import router from './router'
import store from './store'
import { Message } from 'element-ui'
import NProgress from 'nprogress' // progress bar
import 'nprogress/nprogress.css' // progress bar style
import { getBkUid, getToken } from '@/utils/auth' // get token from cookie
import { toLoginPage } from '@/utils/login'
import { ROLE_ADMIN } from '@/store/modules/user'

NProgress.configure({ showSpinner: false }) // NProgress Configuration

const whiteList = ['/login'] // no redirect whitelist

router.beforeEach(async(to, from, next) => {
  // start progress bar
  NProgress.start()

  // set page title
  document.title = process.env.VUE_APP_TITLE

  // determine whether the user has logged in
  // const bkTicket = getBkTicket()
  const bkUid = getBkUid()
  const token = getToken()
  // const hasToken = token || bkTicket || bkUid
  const hasToken = token || bkUid
  if (hasToken) {
    if (to.path === '/login') {
      // if is logged in, redirect to the home page
      next({ path: '/' })
      NProgress.done()
    } else {
      // determine whether the user has obtained his permission roles through getInfo
      const hasRoles = store.getters.roles && store.getters.roles.length > 0
      if (hasRoles) {
        next()
      } else {
        try {
          // get user info
          // note: roles must be a object array! such as: ['admin'] or ,['developer','editor']
          const { roles } = await store.dispatch('user/getInfo')

          // 由于现阶段菜单没配置权限分类，理论上op只有admin可以看,暂时权限不够的跳转404
          if (!roles.includes(ROLE_ADMIN)) {
            router.push(`/404`)
          } else {
            // generate accessible routes map based on roles
            const accessRoutes = await store.dispatch('permission/generateRoutes', roles)

            // dynamically add accessible routes
            router.addRoutes(accessRoutes)

            // hack method to ensure that addRoutes is complete
            // set the replace: true, so the navigation will not leave a history record
            next({ ...to, replace: true })
          }
        } catch (error) {
          // remove token and go to login page to re-login
          await store.dispatch('user/resetToken')
          Message.error(error || 'Has Error')
          toLoginPage(to.path)
          NProgress.done()
        }
      }
    }
  } else {
    /* has no token*/

    if (whiteList.indexOf(to.path) !== -1) {
      // in the free login whitelist, go directly
      next()
    } else {
      // other pages that do not have permission to access are redirected to the login page.
      toLoginPage(to.path)
      NProgress.done()
    }
  }
})

router.afterEach(() => {
  // finish progress bar
  NProgress.done()
})
