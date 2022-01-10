import Vue from 'vue'
import VueRouter from 'vue-router'
import ServiceManager from '../views/service-manager/ServiceManager'

Vue.use(VueRouter)

const routes = [
    {
        path: '/',
        name: 'ServiceManager',
        component: ServiceManager
    }
]

const router = new VueRouter({
    mode: 'history',
    base: process.env.BASE_URL,
    routes
})

export default router
