<template>
    <div class="bkrepo-user-container flex-align-center">
        <div class="mr5 user-info-avatar">
            <div :class="[
                'avatar-letter',
                `avatar-letter-${['green', 'yellow', 'red', 'blue'][(userInfo.name || userInfo.username).length % 4]}`
            ]">
                {{ (userInfo.name || userInfo.username)[0] }}
            </div>
        </div>
        <span class="flex-1 text-overflow" :title="userInfo.name || userInfo.username">{{ userInfo.name || userInfo.username }}</span>
        <i class="ml5 bk-icon icon-angle-down"></i>
        <ul class="user-menu">
            <li class="flex-align-center" v-for="name in menuList" :key="name" @click="changeRoute(name)">
                <router-link
                    class="hover-btn flex-align-center"
                    :to="{ name }"
                    @click.stop.prevent="() => {}">
                    <Icon :name="name" size="14" />
                    <span class="ml8 text-overflow">{{ $t(name) }}</span>
                </router-link>
            </li>
            <li class="hover-btn flex-align-center" @click="logout">
                <Icon name="repoLogout" size="14" />
                <span class="ml8 text-overflow">{{ $t('logout') }}</span>
            </li>
        </ul>
    </div>
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
                ].filter(Boolean)
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
.bkrepo-user-container {
    justify-content: flex-end;
    position: relative;
    width: 130px;
    height: 100%;
    padding: 0 10px;
    cursor: pointer;
    .icon-angle-down {
        transition: all .3s;
        font-size: 22px;
    }
    .user-info-avatar {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        font-size: 0;
        overflow: hidden;
        box-shadow: 0 0 5px 5px rgba(0, 0, 0, 0.1);
        .avatar-letter {
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100%;
            padding-bottom: 4px;
            font-size: 16px;
            color: white;
            &-green {
                background-color: #30D878;
            }
            &-yellow {
                background-color: #FFB400;
            }
            &-red {
                background-color: #FF5656;
            }
            &-blue {
                background-color: #3a84ff;
            }
        }
    }
    &:hover {
        .icon-angle-down {
            transform-origin: center;
            transform: rotate(-180deg);
        }
        .user-menu {
            display: initial;
        }
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
            &:hover {
                a {
                    color: var(--primaryColor);
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
