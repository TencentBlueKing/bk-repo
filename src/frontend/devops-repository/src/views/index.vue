<template>
    <div class="bkrepo-view flex-align-center">
        <div class="nav-submain-list" :class="{ 'hidden-menu': hiddenMenu }" v-if="routerStatus">
            <router-link
                class="nav-submain-item flex-align-center"
                :class="{ 'active-link': breadcrumb.find(route => route.name === name) }"
                v-for="name in menuList.project"
                :key="name"
                :to="{ name }">
                <bk-popover class="menu-icon" :content="$t(name)" placement="right" :disabled="!hiddenMenu">
                    <Icon :name="name" size="14" />
                </bk-popover>
                <span v-if="!hiddenMenu" class="menu-name text-overflow">{{ $t(name) }}</span>
            </router-link>
            <div v-if="projectList.length" class="split-line"></div>
            <template v-if="userInfo.admin">
                <router-link
                    class="nav-submain-item flex-align-center"
                    :class="{ 'active-link': breadcrumb.find(route => route.name === name) }"
                    v-for="name in menuList.global"
                    :key="name"
                    :to="{ name }">
                    <bk-popover class="menu-icon" :content="$t(name)" placement="right" :disabled="!hiddenMenu">
                        <Icon :name="name" size="14" />
                    </bk-popover>
                    <span v-if="!hiddenMenu" class="menu-name text-overflow">{{ $t(name) }}</span>
                </router-link>
            </template>
            <Icon class="hidden-menu-btn"
                @click.native="hiddenMenu = !hiddenMenu"
                :size="14" :name="hiddenMenu ? 'dedent' : 'indent'" />
        </div>
        <div class="m10 bkrepo-view-main flex-column flex-1">
            <breadcrumb class="mb10 repo-breadcrumb" v-if="routerStatus">
                <bk-breadcrumb-item :to="{ name: 'repositories' }">{{$t('repoList')}}</bk-breadcrumb-item>
            </breadcrumb>
            <router-view class="flex-1"></router-view>
        </div>
    </div>
</template>
<script>
    import Breadcrumb from '@repository/components/Breadcrumb/topBreadcrumb'
    import { mapState, mapActions } from 'vuex'
    export default {
        components: { Breadcrumb },
        data () {
            return {
                hiddenMenu: false,
                routerStatus: true
            }
        },
        computed: {
            ...mapState(['userInfo', 'projectList']),
            menuList () {
                const routerName = this.$route.name
                if (routerName === '440') this.routerStatus = false
                if (MODE_CONFIG === 'ci' || this.projectList.length) {
                    const showRepoScan = RELEASE_MODE !== 'community' || SHOW_ANALYST_MENU
                    return {
                        project: [
                            'repositories',
                            'repoSearch',
                            MODE_CONFIG === 'ci' && 'repoToken',
                            showRepoScan && (this.userInfo.admin || this.userInfo.manage) && 'repoScan',
                            (this.userInfo.admin || this.userInfo.manage) && 'userGroup',
                            SHOW_PROJECT_CONFIG_MENU && (!this.userInfo.admin && this.userInfo.manage) && 'projectConfig' // 仅项目管理员
                        ].filter(Boolean),
                        global: [
                            !(MODE_CONFIG === 'ci') && 'projectManage',
                            'userManage',
                            'nodeManage',
                            // 'securityConfig',
                            'planManage',
                            'repoAudit'
                        ].filter(Boolean)
                    }
                }
                return {
                    project: [],
                    global: []
                }
            },
            breadcrumb () {
                return this.$route.meta.breadcrumb || []
            }
        },
        mounted () {
            this.checkPM({ projectId: this.$route.params.projectId })
        },
        methods: {
            ...mapActions([
                'checkPM'
            ])
        }
    }
</script>
<style lang="scss" scoped>
.bkrepo-view {
    height: 100%;
    .nav-submain-list {
        position: relative;
        width: 200px;
        height: 100%;
        overflow-y: auto;
        padding-top: 12px;
        font-size: 14px;
        background-color: white;
        will-change: width;
        transition: width .3s;
        &.hidden-menu {
           width: 46px;
        }
        .split-line {
            height: 1px;
            margin: 6px 16px;
            background-color: var(--fontSubsidiaryColor);
            opacity: 0.2;
        }
        .nav-submain-item {
            height: 44px;
            margin-bottom: 4px;
            padding: 0 16px;
            color: #7b7d8a;
            &:hover {
                color: #3b97ff;
                background-color: #ecf4ff;
            }
            &.router-link-active,
            &.active-link {
                color: #3b97ff;
                background-color: #ecf4ff;
            }
            .menu-icon {
                ::v-deep .bk-tooltip-ref {
                    display: flex;
                }
            }
            .menu-name {
                margin-left: 8px;
            }
        }
        .hidden-menu-btn {
            position: absolute;
            left: 16px;
            bottom: 24px;
            color: #7b7d8a;
            cursor: pointer;
        }
    }
    .bkrepo-view-main {
        height: calc(100% - 20px); // margin
        .repo-breadcrumb {
            height: 20px;
        }
    }
}
</style>
