import { connectToMain, rootPath, subEnv } from '@blueking/sub-saas'
import { routeBase } from '@repository/utils'
import Vue from 'vue'
import Router from 'vue-router'
import routerArr from './router'

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
        base: routeBase,
        routes: routerArr
    })
    console.log(rootPath, routeBase, subEnv)
    connectToMain(router)
    return router
}

export default createRouter
