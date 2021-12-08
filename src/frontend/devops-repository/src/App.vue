<template>
    <div class="bkrepo-main flex-column">
        <Header v-if="!iframeMode" />
        <router-view class="bkrepo-main-container"></router-view>
        <ConfirmDialog />
        <Login v-if="!iframeMode" />
    </div>
</template>

<script>
    import Header from '@/components/Header'
    import ConfirmDialog from '@/components/ConfirmDialog'
    import Login from '@/components/Login'
    import Vue from 'vue'
    import { mapState, mapMutations, mapActions } from 'vuex'
    import cookies from 'js-cookie'
    export default {
        name: 'App',
        components: { Header, ConfirmDialog, Login },
        data () {
            return {
                iframeMode: MODE_CONFIG === 'ci'
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
        created () {
            const username = cookies.get('bk_uid')
            username && this.SET_USER_INFO({ username })

            const urlProjectId = (location.pathname.match(/\/ui\/([^/]+)/) || [])[1]
            const localProjectId = localStorage.getItem('projectId')
            if (this.iframeMode) {
                window.Vue = Vue
                const script = document.createElement('script')
                script.type = 'text/javascript'
                script.src = '/ui/devops-utils.js'
                document.getElementsByTagName('head')[0].appendChild(script)
                script.onload = () => {
                    this.$syncUrl(this.$route.fullPath.replace(/^\/ui\//, '/'))
                    window.globalVue.$on('change::$currentProjectId', data => { // 蓝鲸Devops选择项目时切换
                        localStorage.setItem('projectId', data.currentProjectId)
                        if (this.projectId !== data.currentProjectId) {
                            this.goHome(data.currentProjectId)
                        }
                    })

                    // window.globalVue.$on('change::$routePath', data => { // 蓝鲸Devops切换路径
                    //     this.$router.push({ name: data.routePath.englishName })
                    // })

                    window.globalVue.$on('order::backHome', data => { // 蓝鲸Devops选择项目时切换
                        this.goHome()
                    })

                    // window.globalVue.$on('change::$projectList', data => { // 获取项目列表
                    //     this.SET_PROJECT_LIST(data.projectList)
                    // })

                    window.globalVue.$on('order::syncLocale', locale => {
                        this.$setLocale(locale)
                    })

                    window.globalVue.$on('change::$userInfo', data => { // 用户信息
                        this.SET_USER_INFO(data.userInfo)
                    })

                    // window.globalVue.$on('change::$userList', data => { // 用户信息
                    //     this.SET_USER_LIST(data.userList)
                    // })
                }
                localStorage.setItem('projectId', urlProjectId || localProjectId || '')
                !urlProjectId && this.$router.replace({
                    name: 'repoList',
                    params: {
                        projectId: urlProjectId || localProjectId || ''
                    }
                })
            } else {
                Promise.all([this.ajaxUserInfo(), this.getProjectList(), this.getRepoUserList()]).then(() => {
                    let projectId = ''
                    if (!(urlProjectId && this.projectList.find(v => v.id === urlProjectId))) {
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
                    this.checkPM({ projectId: (projectId || urlProjectId || localProjectId) })
                })
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
            ...mapMutations(['SET_USER_INFO']),
            ...mapActions(['getProjectList', 'ajaxUserInfo', 'checkPM', 'getRepoUserList']),
            goHome (projectId) {
                const params = projectId ? { projectId } : {}
                this.$router.replace({
                    name: 'repoList',
                    params
                })
            }
        }
    }
</script>
<style lang="scss">
@import '@/scss/index';
.bkrepo-main {
    height: 100%;
    background-color: var(--bgWeightColor);
    .bkrepo-main-container {
        flex: 1;
        overflow: hidden;
    }
}
</style>
