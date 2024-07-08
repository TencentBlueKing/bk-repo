
import ConfirmDialog from '@repository/components/ConfirmDialog'
import GlobalUploadViewport from '@repository/components/GlobalUploadViewport'
import { routeBase } from '@repository/utils'
import Vue from 'vue'
import { mapMutations, mapState } from 'vuex'
export default {
    name: 'App',
    components: { ConfirmDialog, GlobalUploadViewport },
    computed: {
        ...mapState(['userInfo', 'projectList']),
        projectId () {
            return this.$route.params.projectId
        }
    },
    watch: {
        '$route.fullPath' (val) { // 同步地址到蓝鲸Devops
            this.$syncUrl?.(val)
        }
    },
    created () {
        const callback = e => {
            const instance = (e.reason || e)
            if (instance instanceof Error) {
                console.error(e)
            } else {
                if (instance.content) {
                    // bk-form表单校验
                } else {
                    instance.message && this.$bkMessage({
                        message: instance.message,
                        theme: 'error'
                    })
                }
            }
        }
        window.addEventListener('unhandledrejection', callback)
        Vue.config.errorHandler = callback
    },
    methods: {
        ...mapMutations(['SET_USER_INFO', 'SET_USER_LIST', 'SET_PROJECT_LIST']),
        goHome (projectId) {
            const params = projectId ? { projectId } : {}
            this.$router.replace({
                name: 'repositories',
                params
            })
        },
        loadDevopsUtils (src) {
            window.Vue = Vue
            const script = document.createElement('script')
            script.type = 'text/javascript'
            script.src = src
            document.getElementsByTagName('head')[0].appendChild(script)
            script.onload = () => {
                this.$syncUrl?.(this.$route.fullPath.replace(/^\/[a-zA-Z0-9]+\//, '/'))
                this.$changeActiveRoutes?.(this.$route?.meta?.breadcrumb?.map(v => v.name) || [])
                window.globalVue.$on('change::$currentProjectId', data => { // 蓝鲸Devops选择项目时切换
                    localStorage.setItem('projectId', data.currentProjectId)
                    if (this.projectId !== data.currentProjectId) {
                        this.goHome(data.currentProjectId)
                    }
                })

                window.globalVue.$on('change::$routePath', data => { // 蓝鲸Devops切换路径
                    console.log('change::$routePath', data)
                    this.$router.push({ name: data.routePath.englishName, path: data.routePath.path.replace(/^\/[a-zA-Z]+/, routeBase) })
                })

                window.globalVue.$on('order::backHome', data => { // 蓝鲸Devops选择项目时切换
                    this.goHome()
                })

                window.globalVue.$on('change::$projectList', data => { // 获取项目列表
                    this.SET_PROJECT_LIST(data.projectList)
                })

                window.globalVue.$on('order::syncLocale', locale => {
                    this.$setLocale?.(locale)
                })

                window.globalVue.$on('change::$userInfo', data => { // 用户信息
                    this.SET_USER_INFO(data.userInfo)
                })

                window.globalVue.$on('change::$userList', data => { // 用户信息
                    this.SET_USER_LIST(data.userList)
                })
            }
        }
    }
}
