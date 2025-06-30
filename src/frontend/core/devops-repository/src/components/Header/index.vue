<template>
    <div class="bkrepo-header flex-align-center">
        <div class="flex-align-center">
            <router-link class="flex-align-center bkrepo-logo" :to="{ name: 'repositories' }">
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
                v-if="showProjectSelect"
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
                <bk-option
                    v-for="option in projectList"
                    :key="option.id"
                    :id="option.id"
                    :name="option.name">
                </bk-option>
                <div slot="extension" style="cursor: pointer;" class="flex-align-center">
                    <div @click="createProject" class="hover-extent" style="width: 45%">
                        <i class="bk-icon icon-plus-circle"></i>
                        {{$t('createProject')}}
                    </div>
                    <bk-divider direction="vertical"></bk-divider>
                    <div style="width: 45%" @click="manageProject">
                        <i class="bk-icon icon-apps mr5"></i>
                        {{$t('manageProject')}}
                    </div>
                </div>
            </bk-select>
        </div>
        <div style="text-align: end" class="language-select">
            <bk-popover
                theme="light navigation-message"
                placement="bottom"
                :arrow="false"
                trigger="click"
                ref="popoverRef"
                class="popover"
            >
                <div class="flag-box">
                    <Icon :name="curLang.icon" size="20" />
                </div>
                <template slot="content">
                    <li
                        v-for="(item, index) in icons"
                        :key="index"
                        :class="['bkci-dropdown-item']"
                        @click="changeLanguage(item.id)">
                        <Icon class="mr5" :name="item.icon" style="vertical-align: top;margin-bottom: 2px;" size="20" />
                        {{ item.name }}
                    </li>
                </template>
            </bk-popover>
            <bk-popover
                theme="light navigation-message"
                placement="bottom"
                :arrow="false"
                trigger="click"
                ref="popoverRef"
                class="popover"
            >
                <div class="flag-box">
                    <Icon name="help-document" size="20" />
                </div>
                <template slot="content">
                    <li
                        v-for="(item, index) in helps"
                        :key="index"
                        :class="['bkci-dropdown-item']"
                        @click="clickHelps(item.id)">
                        {{ $t(`helps.${item.name}`) }}
                    </li>
                </template>
            </bk-popover>
        </div>
        <User />
        <project-info-dialog ref="projectInfoDialog"></project-info-dialog>
        <version-log ref="versionLogDialog"></version-log>
    </div>
</template>
<script>
    import User from '@repository/components/User'
    import projectInfoDialog from '@repository/views/projectManage/projectInfoDialog'
    import { mapState, mapActions } from 'vuex'
    import cookies from 'js-cookie'
    import VersionLog from '@repository/components/VersionLog'
    import { getVersionContext } from '@repository/utils/versionLogs'
    export default {
        name: 'BkrepoHeader',
        components: { VersionLog, User, projectInfoDialog },
        props: {
            icons: {
                type: Array,
                default: () => [
                    {
                        icon: 'chinese',
                        name: '中文',
                        id: 'zh-cn'
                    },
                    {
                        icon: 'english',
                        name: 'English',
                        id: 'en'
                    },
                    {
                        icon: 'japanese',
                        name: '日语',
                        id: 'ja'
                    }
                ]
            }
        },
        data () {
            return {
                language: '',
                curLang: {
                    id: '',
                    icon: ''
                },
                helps: [
                    {
                        name: 'documentation',
                        id: 'documentation'
                    },
                    {
                        name: 'releaseNote',
                        id: 'releaseNote'
                    },
                    {
                        name: 'feedback',
                        id: 'feedback'
                    },
                    {
                        name: 'openSource',
                        id: 'openSource'
                    }
                ],
                showProjectSelect: true
            }
        },
        computed: {
            ...mapState(['projectList', 'userInfo', 'versionLogs']),
            projectId () {
                return this.$route.params.projectId
            }
        },
        watch: {
            '$route' (to, from) {
                if (to.name === 'filePreview' || to.name === 'outsideFilePreview') {
                    this.showProjectSelect = false
                } else {
                    this.showProjectSelect = true
                }
                this.language = cookies.get('blueking_language') || 'zh-cn'
                this.curLang = this.icons.find(item => item.id === this.language) || { id: 'zh-cn', icon: 'chinese' }
                if (this.language !== 'zh-cn') {
                    this.$refs.porjectSelect.$el.style.width = '380px'
                } else {
                    this.$refs.porjectSelect.$el.style.width = '300px'
                }
            }
        },
        methods: {
            ...mapActions(['checkPM', 'getPermissionUrl']),
            changeProject (projectId) {
                localStorage.setItem('projectId', projectId)
                if (this.projectId === projectId) return
                this.checkPM({ projectId })
                this.$router.push({
                    name: 'repositories',
                    params: {
                        projectId
                    },
                    query: this.$route.query
                })
            },
            changeLanguage (id) {
                const BK_CI_DOMAIN = location.host.split('.').slice(1).join('.')
                if (id === 'en') {
                    cookies.remove('blueking_language', { domain: BK_CI_DOMAIN, path: '/' })
                    cookies.set('blueking_language', 'en', { domain: BK_CI_DOMAIN, path: '/', expires: 366 })
                    location.reload()
                } else if (id === 'zh-cn') {
                    cookies.remove('blueking_language', { domain: BK_CI_DOMAIN, path: '/' })
                    cookies.set('blueking_language', 'zh-cn', { domain: BK_CI_DOMAIN, path: '/', expires: 366 })
                    location.reload()
                } else {
                    cookies.remove('blueking_language', { domain: BK_CI_DOMAIN, path: '/' })
                    cookies.set('blueking_language', 'ja', { domain: BK_CI_DOMAIN, path: '/', expires: 366 })
                    location.reload()
                }
            },
            joinProject () {
                this.getPermissionUrl({
                    body: {
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
            },
            async clickHelps (id) {
                const languagePath = this.language === 'zh-cn' ? 'ZH' : 'EN'
                const url = DOC_URL + '/markdown/' + languagePath + '/Devops/3.0/UserGuide/intro/README.md'
                switch (id) {
                    case 'documentation':
                        window.open(url, '_blank')
                        break
                    case 'releaseNote':
                        if (this.versionLogs.length > 0) {
                            this.$refs.versionLogDialog.show = true
                            this.$refs.versionLogDialog.versionLogs = this.versionLogs
                            this.$refs.versionLogDialog.markdown = await getVersionContext(this.versionLogs[0].version)
                        }
                        break
                    case 'feedback':
                        window.open('https://bk.tencent.com/s-mart/community/question', '_blank')
                        break
                    default:
                        window.open('https://github.com/TencentBlueKing/bk-repo', '_blank')
                }
            },
            async showVersionLogs () {
                if (this.versionLogs.length > 0) {
                    this.$refs.versionLogDialog.show = true
                    this.$refs.versionLogDialog.versionLogs = this.versionLogs
                    this.$refs.versionLogDialog.markdown = await getVersionContext(this.versionLogs[0].version)
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.bkrepo-header {
    height: 50px;
    padding: 0 20px;
    justify-content: space-between;
    background-color:  #191929;
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
        margin-left: auto;
    }
}
.flag-box{
    align-items: center;
    border-radius: 50%;
    cursor: pointer;
    display: inline-flex;
    font-size: 16px;
    height: 32px;
    justify-content: center;
    position: relative;
    width: 32px;
    margin-right: 8px;
    color: #7b7d8a;
    &:hover {
        color: #f1ffff;
        background-color: #253146;
    }
}
.flag-box-help{
    align-items: center;
    border-radius: 50%;
    cursor: pointer;
    display: inline-flex;
    font-size: 16px;
    height: 32px;
    justify-content: center;
    position: relative;
    width: 32px;
    margin-right: 8px;
    color: #7b7d8a;
    &:hover {
        color: #f1ffff;
        background-color: #253146;
    }
}
.bkci-dropdown-item {
    display: flex;
    align-items: center;
    height: 32px;
    line-height: 33px;
    padding: 0 16px;
    color: #63656e;
    font-size: 12px;
    text-decoration: none;
    white-space: nowrap;
    background-color: #fff;
    cursor: pointer;
    &:hover {
        background-color: #EAF3FF;
        color: #6BA3FF;
    }
    &.disabled {
        color: #dcdee5;
        cursor: not-allowed;
    }
    &.active {
        background-color: #f5f7fb;
    }
}
</style>
