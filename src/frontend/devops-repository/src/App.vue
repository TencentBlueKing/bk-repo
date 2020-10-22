<template>
    <div id="app">
        <router-view></router-view>
    </div>
</template>

<script>
    import Vue from 'vue'
    import { mapMutations } from 'vuex'
    export default {
        name: 'App',
        watch: {
            '$route.fullPath' (val) { // 同步地址到蓝鲸Devops
                this.$syncUrl && this.$syncUrl(val.replace(/^\/ui\//, '/'))
            }
        },
        created () {
            const script = document.createElement('script')
            script.type = 'text/javascript'
            script.src = DEVOPS_SITE_URL + '/console/static/devops-utils.js'
            document.getElementsByTagName('head')[0].appendChild(script)
            script.onload = () => {
                this.$syncUrl(this.$route.fullPath.replace(/^\/ui\//, '/'))
                window.globalVue.$on('change::$currentProjectId', data => { // 蓝鲸Devops选择项目时切换
                    if (this.$route.params.projectId !== data.currentProjectId) {
                        this.goHome(data.currentProjectId)
                    }
                })
                window.globalVue.$on('order::backHome', data => { // 蓝鲸Devops选择项目时切换
                    this.goHome()
                })

                window.globalVue.$on('change::$projectList', data => { // 获取项目列表
                    // this.$store.dispatch('setProjectList', this.$projectList)
                    // this.$store.dispatch('getProjectList')
                })

                window.globalVue.$on('order::syncLocale', locale => {
                    this.$setLocale(locale)
                })
                this.getUserList()
                this.getUserInfo()
            }
            const callback = e => {
                this.$bkMessage({
                    message: (e.reason || e).message,
                    theme: 'error'
                })
            }
            window.addEventListener('unhandledrejection', callback)
            Vue.config.errorHandler = callback
        },
        methods: {
            ...mapMutations(['SET_USER_INFO', 'SET_USER_LIST']),
            goHome (projectId) {
                const params = projectId ? { projectId } : {}
                this.$router.replace({
                    name: 'repoList',
                    params
                })
            },
            getUserList () {
                if (this.$userList) this.SET_USER_LIST(this.$userList)
                else {
                    setTimeout(() => {
                        this.getUserList()
                    }, 1000)
                }
            },
            getUserInfo () {
                if (this.$userInfo) this.SET_USER_INFO(this.$userInfo)
                else {
                    setTimeout(() => {
                        this.getUserInfo()
                    }, 1000)
                }
            }
        }
    }
</script>
<style lang="scss">
@import '@/scss/index.scss';
#app {
    height: 100%;
    padding: 20px;
    background-color: $bgLightColor;
}
</style>
