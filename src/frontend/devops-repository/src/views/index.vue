<template>
    <div class="bkrepo-view flex-align-center">
        <div class="nav-submain-list" :class="{ 'hidden-menu': hiddenMenu }">
            <router-link
                class="nav-submain-item flex-align-center"
                :class="{ 'active-link': $route.meta.breadcrumb.find(route => route.name === name) }"
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
                    :class="{ 'active-link': $route.meta.breadcrumb.find(route => route.name === name) }"
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
            <breadcrumb class="mb10 repo-breadcrumb">
                <bk-breadcrumb-item :to="{ name: 'repoList' }">
                    <svg width="48" height="16" style="vertical-align:-3px">
                        <use xlink:href="#vpack" />
                    </svg>
                </bk-breadcrumb-item>
            </breadcrumb>
            <router-view class="flex-1"></router-view>
        </div>
    </div>
</template>
<script>
    import Breadcrumb from '@repository/components/Breadcrumb/topBreadcrumb'
    import { mapState, mapGetters } from 'vuex'
    export default {
        components: { Breadcrumb },
        data () {
            return {
                hiddenMenu: false
            }
        },
        computed: {
            ...mapState(['userInfo', 'projectList']),
            ...mapGetters(['masterNode']),
            menuList () {
                return {
                    project: [
                        (MODE_CONFIG === 'ci' || this.projectList.length) && 'repoList',
                        (MODE_CONFIG === 'ci' || this.projectList.length) && 'repoSearch',
                        MODE_CONFIG === 'ci' && 'repoToken',
                        (MODE_CONFIG === 'ci' || this.projectList.length) && this.userInfo.admin && this.isMasterNode && 'planManage',
                        (MODE_CONFIG === 'ci' || this.projectList.length) && !this.userInfo.admin && this.userInfo.manage && 'projectConfig'
                    ].filter(Boolean),
                    global: [
                        MODE_CONFIG !== 'ci' && 'projectManage',
                        'userManage',
                        'nodeManage',
                        'repoAudit'
                    ].filter(Boolean)
                }
            },
            isMasterNode () {
                return this.masterNode.url && this.masterNode.url.indexOf(location.origin) !== -1
            }
        }
    }
</script>
<style lang="scss" scoped>
.bkrepo-view {
    height: 100%;
    .nav-submain-list {
        position: relative;
        width: 180px;
        height: 100%;
        overflow-y: auto;
        padding-top: 12px;
        font-size: 14px;
        background-color: var(--deepBgColor);
        will-change: width;
        transition: width .3s;
        &.hidden-menu {
           width: 46px;
        }
        .split-line {
            height: 1px;
            margin: 6px 16px;
            background-color: white;
            opacity: 0.2;
        }
        .nav-submain-item {
            height: 44px;
            margin-bottom: 4px;
            padding: 0 16px;
            color: rgba(255, 255, 255, 0.8);
            &:hover {
                color: white;
                background-color: #407BE0;
            }
            &.router-link-active,
            &.active-link {
                color: var(--primaryColor);
                background-color: white;
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
            color: white;
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
