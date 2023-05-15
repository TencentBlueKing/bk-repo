<template>
    <div class="bkrepo-header flex-align-center">
        <div class="flex-align-center">
            <router-link class="flex-align-center bkrepo-logo" :to="{ name: 'repoList' }">
                <svg
                    :width="34"
                    :height="34"
                    style="fill: currentColor"
                >
                    <use xlink:href="#repository" />
                </svg>
                <header class="ml10 bkrepo-title">{{ $t('RepoManage') }}</header>
            </router-link>
            <!-- <a class="ml20 link" target="_self" href="/software/repoList">
                <i class="devops-icon icon-sort"></i>
                <span class="ml5">Artifact Hub</span>
            </a> -->
            <bk-select
                ref="porjectSelect"
                class="ml20 bkre-project-select"
                :value="projectId"
                searchable
                :clearable="false"
                :placeholder="$t('inputProject')"
                @change="changeProject"
                size="small"
                :enable-virtual-scroll="projectList && projectList.length > 3000"
                :list="projectList">
                <bk-option v-for="option in projectList"
                    :key="option.id"
                    :id="option.id"
                    :name="option.name">
                </bk-option>
                <div slot="extension" style="cursor: pointer;" class="flex-align-center">
                    <div @click="createProject" class="hover-extent">
                        <i class="bk-icon icon-plus-circle"></i>
                        {{$t('createProject')}}
                        <bk-divider direction="vertical"></bk-divider>
                    </div>
                    <div @click="joinProject">
                        <img width="16" height="16" style="float: left;margin-top: 7px;margin-right: 3px" src="/ui/project-add.svg" />
                        {{$t('joinProject')}}
                        <bk-divider direction="vertical"></bk-divider>
                    </div>
                    <div @click="manageProject">
                        <i class="bk-icon icon-apps mr5"></i>
                        {{$t('manageProject')}}
                    </div>
                </div>
            </bk-select>
        </div>
        <div style="text-align: end" @click="changeLanguage" class="language-select">
            <img width="20" height="20" style="background-color: currentColor;float: left;margin-right: 3px" src="/ui/locale.svg" />
            <span>{{ language === 'zh-cn' ? 'English' : '中文' }}</span>
        </div>
        <User />
        <project-info-dialog ref="projectInfoDialog"></project-info-dialog>
    </div>
</template>
<script>
    import User from '@repository/components/User'
    import projectInfoDialog from '@repository/views/projectManage/projectInfoDialog'
    import { mapState, mapActions } from 'vuex'
    import cookies from 'js-cookie'
    export default {
        name: 'bkrepoHeader',
        components: { User, projectInfoDialog },
        data () {
            return {
                language: ''
            }
        },
        computed: {
            ...mapState(['projectList', 'userInfo']),
            projectId () {
                return this.$route.params.projectId
            }
        },
        created () {
            this.language = cookies.get('blueking_language') || 'zh-cn'
            this.$nextTick(() => {
                if (this.language !== 'zh-cn') {
                    this.$refs.porjectSelect.$el.style.width = '400px'
                }
            })
        },
        methods: {
            ...mapActions(['checkPM', 'getPermissionUrl']),
            changeProject (projectId) {
                localStorage.setItem('projectId', projectId)
                if (this.projectId === projectId) return
                this.checkPM({ projectId })
                this.$router.push({
                    name: 'repoList',
                    params: {
                        projectId
                    },
                    query: this.$route.query
                })
            },
            changeLanguage () {
                const BK_CI_DOMAIN = location.host.split('.').slice(1).join('.')
                if (this.language === 'zh-cn') {
                    cookies.remove('blueking_language', { domain: BK_CI_DOMAIN, path: '/' })
                    cookies.set('blueking_language', 'en', { domain: BK_CI_DOMAIN, path: '/' })
                    location.reload()
                } else {
                    cookies.remove('blueking_language', { domain: BK_CI_DOMAIN, path: '/' })
                    cookies.set('blueking_language', 'zh-cn', { domain: BK_CI_DOMAIN, path: '/' })
                    location.reload()
                }
            },
            joinProject () {
                this.getPermissionUrl({
                    body: {
                        projectId: this.projectId,
                        action: 'READ',
                        resourceType: 'PROJECT',
                        uid: this.userInfo.name
                    }
                }).then(res => {
                    if (res !== '') {
                        window.open(res, '_blank')
                    }
                })
            },
            createProject (project = {}) {
                const { id = '', name = '', description = '' } = project
                this.$refs.projectInfoDialog.setData({
                    show: true,
                    loading: false,
                    add: !id,
                    id,
                    name,
                    description
                })
            },
            manageProject () {
                this.$router.replace({
                    name: 'projectManage'
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.bkrepo-header {
    height: 50px;
    padding: 0 20px;
    justify-content: space-between;
    background-color:  var(--fontPrimaryColor);
    color: white;
    .bkre-project-select {
        width: 300px;
        color: white;
        border-color: #FFFFFF33;
        background-color: #FFFFFF1A;
        &:hover {
            background-color: rgba(255, 255, 255, 0.4);
        }
    }
    .bkrepo-logo {
        color: white;
    }
    .bkrepo-title {
        font-size: 18px;
        letter-spacing: 1px;
    }
    .link {
        padding: 0 10px;
        color: white;
        line-height: 24px;
        border: 1px solid #FFFFFF33;
        background-color: #FFFFFF1A;
        .icon-sort {
            display:inline-block;
            font-size: 12px;
            transform: rotate(90deg);
        }
        &:hover {
            background-color: rgba(255, 255, 255, 0.4);
        }
    }
    .language-select:hover{
        cursor: pointer;
    }
    .language-select {
        margin-left: auto
    }
}
</style>
