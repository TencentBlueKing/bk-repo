import Vue from 'vue'
import Router from 'vue-router'
import routerArr from './router'

Vue.use(Router)

const createRouter = (store) => {
    const router = new Router({
        mode: 'history',
        routes: routerArr
    })

    router.beforeEach((to, from, next) => {
        next()
    })

    router.afterEach(route => {
    })
    return router
}

export default createRouter
