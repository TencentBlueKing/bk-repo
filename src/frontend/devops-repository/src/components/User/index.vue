<template>
    <bk-popover
        theme="light navigation-message"
        placement="bottom"
        :arrow="false"
        trigger="click"
        ref="popoverRef"
    >
        <div class="user-entry">
            {{ userInfo.name || userInfo.username }}
            <i class="devops-icon icon-down-shape ml5" />
        </div>
        <template slot="content">
            <li class="bkci-dropdown-item" v-for="name in menuList" :key="name" @click="changeRoute(name)">
                <router-link
                    class="flex-align-center"
                    :to="{ name }"
                    @click.stop.prevent="() => {}">
                    <span class="user-menu-item">{{ $t(name) }}</span>
                </router-link>
            </li>
            <li class="bkci-dropdown-item" @click="logout" style="padding: 0px">
                <span class="user-menu-item">{{ $t('logout') }}</span>
            </li>
        </template>
    </bk-popover>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'bkrepoUser',
        computed: {
            ...mapState(['userInfo']),
            menuList () {
                return [
                    'userCenter',
                    'repoToken'
                    // 'repoHelp'
                ]
            }
        },
        methods: {
            ...mapActions(['logout']),
            changeRoute (route) {
                this.$router.push({
                    name: route
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.user-entry {
    display: flex;
    height: 32px;
    line-height: 32px;
    padding: 0 12px;
    align-items: center;
    &:hover{
        cursor: pointer;
        color: #1848DE;
    }
}
.flex-align-center {
    width: 100%;
    text-align: center;
}
.bkci-dropdown-item {
    display: flex;
    align-items: center;
    height: 32px;
    line-height: 33px;
    padding: 0;
    width: 90px;
    color: #63656e;
    font-size: 12px;
    text-decoration: none;
    white-space: nowrap;
    background-color: #fff;
    cursor: pointer;
    &.disabled {
        color: #dcdee5;
        cursor: not-allowed;
    }
    &.active {
        background-color: #f5f7fb;
    }
}
.user-menu-item {
    color: #737987;
    cursor: pointer;
    width: 100%;
    text-align: center;
    &:hover {
        background-color: #EAF3FF;
        color: #6BA3FF;
    }
}
.bkrepo-user-container {
    justify-content: flex-end;
    position: relative;
    height: 100%;
    padding: 0 10px;
    cursor: pointer;
    .icon-angle-down {
        transition: all .3s;
        font-size: 22px;
    }
    .user-menu {
        display: none;
        position: absolute;
        top: 50px;
        width: 100%;
        padding: 10px 0 4px;
        box-shadow: 0 3px 6px rgba(51, 60, 72, 0.12);
        color: var(--fontPrimaryColor);
        background-color: white;
        z-index: 3000;
        li {
            padding: 0 16px;
            margin-bottom: 6px;
            height: 30px;
            a {
                color: var(--fontPrimaryColor);
            }
            &:hover {
                a {
                    color: var(--primaryWeightColor);
                }
                background-color: var(--bgHoverLighterColor);
            }
        }
        .ml8 {
            margin-left: 8px;
        }
    }
}
</style>
