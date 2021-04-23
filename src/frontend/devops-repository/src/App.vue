<template>
    <div id="app" class="flex-column" v-bkloading="{ isLoading }">
        <Header v-if="mode !== 'ci'" />
        <main class="bkrepo-main-container"
            :style="{
                height: mode === 'ci' ? '100%' : 'calc(100% - 50px)'
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
    import cookies from 'js-cookie'
    export default {
        name: 'App',
        components: { Login, Header },
        data () {
            return {
                isLoading: false
            }
        },
        computed: {
            ...mapState(['projectList']),
            mode () {
                return MODE_CONFIG
            },
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
            const username = cookies.get('bk_uid')
            username && this.SET_USER_INFO({ username })

            const urlProjectId = (location.pathname.match(/\/ui\/([^/]+)/) || [])[1]
            const localProjectId = localStorage.getItem('projectId')
            if (this.mode === 'ci') {
                window.Vue = Vue
                const script = document.createElement('script')
                script.type = 'text/javascript'
                script.src = location.origin + '/ui/devops-utils.js'
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
                !urlProjectId && this.$router.replace({
                    name: 'repoList',
                    params: {
                        projectId: urlProjectId || localProjectId || ''
                    }
                })
            } else {
                this.isLoading = true
                await Promise.all([this.ajaxUserInfo(), this.getProjectList(), this.getRepoUserList()])
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
            ...mapActions(['getRepoUserList', 'getProjectList', 'ajaxUserInfo']),
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
