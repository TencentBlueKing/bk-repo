<template>
    <div id="app" class="flex-column" v-bkloading="{ isLoading }">
        <Header v-if="!iframeMode" />
        <main class="bkrepo-main-container"
            :style="{
                height: iframeMode ? '100%' : 'calc(100% - 50px)'
            }">
            <router-view></router-view>
        </main>
        <Login />
    </div>
</template>

<script>
    import Header from '@/components/Header'
    import Login from '@/components/Login'
    import Vue from 'vue'
    import { mapState, mapMutations, mapActions } from 'vuex'
    export default {
        name: 'App',
        components: { Login, Header },
        data () {
            return {
                isLoading: false,
                iframeMode: PAAS_CONFIG
            }
        },
        computed: {
            ...mapState(['projectList']),
            projectId () {
                return this.$route.params.projectId
            }
        },
        watch: {
            '$route.fullPath' (val) { // 同步地址到蓝鲸Devops
                this.$syncUrl && this.$syncUrl(val.replace(/^\/ui\//, '/'))
            }
        },
        async created () {
            const urlProjectId = (location.pathname.match(/(?<=\/ui\/)[^/]*/) || [])[0]
            const localProjectId = localStorage.getItem('projectId')
            if (this.iframeMode) {
                const script = document.createElement('script')
                script.type = 'text/javascript'
                script.src = DEVOPS_SITE_URL + '/console/static/devops-utils.js'
                document.getElementsByTagName('head')[0].appendChild(script)
                script.onload = () => {
                    this.$syncUrl(this.$route.fullPath.replace(/^\/ui\//, '/'))
                    window.globalVue.$on('change::$currentProjectId', data => { // 蓝鲸Devops选择项目时切换
                        localStorage.setItem('projectId', data.currentProjectId)
                        if (this.projectId !== data.currentProjectId) {
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
                localStorage.setItem('projectId', urlProjectId || localProjectId || '')
                this.$router.replace({
                    name: 'repoList',
                    params: {
                        projectId: urlProjectId || localProjectId
                    }
                })
            } else {
                this.isLoading = true
                await Promise.all([this.ajaxUserInfo(), this.getProjectList()])
                if (!(urlProjectId && this.projectList.find(v => v.id === urlProjectId))) {
                    let projectId = ''
                    if (this.projectList.find(v => v.id === localProjectId)) {
                        projectId = localProjectId
                    } else {
                        projectId = (this.projectList[0] || {}).id
                    }
                    this.$router.replace({
                        name: 'repoList',
                        params: {
                            projectId
                        }
                    })
                }
                this.isLoading = false
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
            ...mapActions(['getProjectList', 'ajaxUserInfo']),
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
@import '@/scss/index';
#app {
    height: 100%;
    background-color: $bgLightColor;
}
.bkrepo-main-container {
    padding: 20px;
}
</style>
