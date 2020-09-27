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
        if (!from.name && to.name === 'repoList') {
            const projectId = to.params.projectId
            const repositoryHistory = JSON.parse(localStorage.getItem('repositoryHistory') || '{}')[projectId] || { type: 'generic', name: 'custom' }
            if (repositoryHistory.type === 'generic') {
                next({
                    name: 'repoGeneric',
                    params: {
                        ...to.params,
                        repoType: repositoryHistory.type
                    },
                    query: {
                        name: repositoryHistory.name
                    }
                })
            } else {
                next({
                    name: 'repoCommon',
                    params: {
                        ...to.params,
                        repoType: repositoryHistory.type
                    },
                    query: {
                        name: repositoryHistory.name
                    }
                })
            }
        } else {
            next()
        }
    })

    router.afterEach(route => {
    })
    return router
}

export default createRouter
