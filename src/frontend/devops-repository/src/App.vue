<template>
    <div class="bkrepo-main flex-column">
        <Notice v-if="!ciMode && !isSubSaas" />
        <Header v-if="!ciMode && !isSubSaas" />
        <router-view class="bkrepo-main-container"></router-view>
        <ConfirmDialog />
        <GlobalUploadViewport />
        <Login v-if="!ciMode && !isSubSaas" />
    </div>
</template>

<script>
    import { subEnv } from '@blueking/sub-saas'
    import mixin from '@repository/AppMixin'
    import Header from '@repository/components/Header'
    import Notice from '@repository/components/Notice'
    import Login from '@repository/components/Login'
    import cookies from 'js-cookie'
    import { mapActions } from 'vuex'
    export default {
        components: { Header, Login, Notice },
        mixins: [mixin],
        data () {
            return {
                ciMode: MODE_CONFIG === 'ci'
            }
        },
        computed: {
            isSubSaas () {
                return subEnv
            }
        },
        created () {
            const username = cookies.get('bk_uid')
            username && this.SET_USER_INFO({ username })
            this.getPermissionDialogConfig()
            if (!this.isSubSaas && this.ciMode) {
                this.loadDevopsUtils('/ui/devops-utils.js')
                // 请求管理员信息
                this.ajaxUserInfo().then((userInfo) => {
                    userInfo.admin && this.getClusterList()
                })
            } else {
                const urlProjectId = (location.pathname.match(/^\/[a-zA-Z0-9]+\/([^/]+)/) || [])[1]
                const localProjectId = localStorage.getItem('projectId')
                Promise.all([this.ajaxUserInfo(), this.getProjectList(), this.getRepoUserList()]).then(([userInfo]) => {
                    if (!this.ciMode && !this.projectList.length) {
                        if (userInfo.admin) {
                            // TODO: 管理员创建项目引导页
                            this.$bkMessage({
                                message: this.$t('noProjectData'),
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
                                message: this.$t('noProjectData'),
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
                            name: 'repositories',
                            params: {
                                projectId
                            }
                        })
                    }

                    userInfo.admin && this.getClusterList()
                })
            }
        },
        methods: {
            ...mapActions([
                'getProjectList',
                'ajaxUserInfo',
                'getRepoUserList',
                'getClusterList',
                'getPermissionDialogConfig'
            ])
        }
    }
</script>
<style lang="scss">
@import '@repository/scss/index';
.navigation-message-theme{
    padding: 0 !important;
}
.bkrepo-main {
    height: 100%;
    background-color: var(--bgWeightColor);
    .bkrepo-main-container {
        flex: 1;
        overflow: hidden;
    }
}
</style>
