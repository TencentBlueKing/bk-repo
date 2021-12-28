<template>
    <div class="bkrepo-main flex-column">
        <Header v-if="!iframeMode" />
        <router-view class="bkrepo-main-container"></router-view>
        <ConfirmDialog />
        <Login v-if="!iframeMode" />
    </div>
</template>

<script>
    import Header from '@repository/components/Header'
    import Login from '@repository/components/Login'
    import { mapActions } from 'vuex'
    import cookies from 'js-cookie'
    import mixin from '@repository/AppMixin'
    export default {
        components: { Header, Login },
        mixins: [mixin],
        data () {
            return {
                iframeMode: MODE_CONFIG === 'ci'
            }
        },
        created () {
            const username = cookies.get('bk_uid')
            username && this.SET_USER_INFO({ username })

            if (this.iframeMode) {
                this.loadDevopsUtils('/ui/devops-utils.js')
            } else {
                const urlProjectId = (location.pathname.match(/^\/[a-zA-Z0-9]+\/([^/]+)/) || [])[1]
                const localProjectId = localStorage.getItem('projectId')
                Promise.all([this.ajaxUserInfo(), this.getProjectList(), this.getRepoUserList()]).then(() => {
                    if (!this.iframeMode && !this.projectList.length) {
                        if (this.userInfo.admin) {
                            // TODO: 管理员创建项目引导页
                            this.$bkMessage({
                                message: '无项目数据',
                                theme: 'error'
                            })
                            this.$router.replace({
                                name: 'projectManage',
                                params: {
                                    projectId: urlProjectId || localProjectId || 'default'
                                }
                            })
                        } else {
                            // TODO: 普通用户无项目提示页
                            this.$bkMessage({
                                message: '无项目数据',
                                theme: 'error'
                            })
                            this.$router.replace({
                                name: 'repoToken',
                                params: {
                                    projectId: urlProjectId || localProjectId || 'default'
                                }
                            })
                        }
                    } else {
                        let projectId = ''
                        if (this.projectList.find(v => v.id === urlProjectId)) {
                            projectId = urlProjectId
                        } else if (this.projectList.find(v => v.id === localProjectId)) {
                            projectId = localProjectId
                        } else {
                            projectId = (this.projectList[0] || {}).id
                        }
                        localStorage.setItem('projectId', projectId)

                        projectId && projectId !== urlProjectId && this.$router.replace({
                            name: 'repoList',
                            params: {
                                projectId
                            }
                        })

                        projectId && this.checkPM({ projectId })
                    }
                })
            }
        },
        methods: {
            ...mapActions(['getProjectList', 'ajaxUserInfo', 'checkPM', 'getRepoUserList'])
        }
    }
</script>
<style lang="scss">
@import '@repository/scss/index';
.bkrepo-main {
    height: 100%;
    background-color: var(--bgWeightColor);
    .bkrepo-main-container {
        flex: 1;
        overflow: hidden;
    }
}
</style>
