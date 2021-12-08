import Vue from 'vue'
import Router from 'vue-router'
import routerArr from './router'

// query参数相同导致的错误
const routerReplace = Router.prototype.replace
Router.prototype.replace = function (location, onResolve, onReject) {
    if (onResolve || onReject) return routerReplace.call(this, location, onResolve, onReject)
    return routerReplace.call(this, location).catch(() => {})
}

Vue.use(Router)

const createRouter = (store) => {
    const router = new Router({
        mode: 'history',
        routes: routerArr
    })
    
    return router
}

export default createRouter
