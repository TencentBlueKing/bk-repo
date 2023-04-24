import Vue from 'vue'
import Router from 'vue-router'
import routerArr from './router'
import createLocale from '@locale'

const { i18n } = createLocale(require.context('@locale/repository/', false, /\.json$/))

// 相同路由导致的错误
const routerReplace = Router.prototype.replace
Router.prototype.replace = function (location, onResolve, onReject) {
    if (onResolve || onReject) return routerReplace.call(this, location, onResolve, onReject)
    return routerReplace.call(this, location).catch(() => {})
}

const routerPush = Router.prototype.push
Router.prototype.push = function (location, onResolve, onReject) {
    if (onResolve || onReject) return routerPush.call(this, location, onResolve, onReject)
    return routerPush.call(this, location).catch(() => {})
}

Vue.use(Router)

const createRouter = () => {
    const router = new Router({
        mode: 'history',
        routes: routerArr
    })

    router.beforeEach((to, from, next) => {
        document.title = i18n.t('webTitle')
        next()
    })

    return router
}

export default createRouter
