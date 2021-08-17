<template>
    <div class="bkrepo-user-container flex-align-center">
        <span>{{ userInfo.name || userInfo.username }}</span>
        <i class="ml5 bk-icon icon-angle-down"></i>
        <ul class="user-menu">
            <li class="flex-align-center" v-for="name in menuList" :key="name">
                <router-link class="hover-btn" :to="{ name }">{{ $t(name) }}</router-link>
            </li>
            <li class="flex-align-center">
                <span class="hover-btn" @click="logout">{{ $t('logout') }}</span>
            </li>
        </ul>
    </div>
</template>
<script>
    import { mapState, mapGetters, mapMutations, mapActions } from 'vuex'
    export default {
        name: 'bkrepoUser',
        computed: {
            ...mapState(['userInfo']),
            ...mapGetters(['masterNode']),
            menuList () {
                return [
                    'userCenter',
                    this.userInfo.admin && 'userManage',
                    this.userInfo.admin && this.isMasterNode && 'nodeManage',
                    this.isMasterNode && 'planManage'
                ].filter(Boolean)
            },
            isMasterNode () {
                return this.masterNode.url && this.masterNode.url.indexOf(location.origin) !== -1
            }
        },
        created () {
            this.getClusterList()
        },
        methods: {
            ...mapMutations(['SHOW_LOGIN_DIALOG']),
            ...mapActions(['getClusterList', 'logout'])
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.bkrepo-user-container {
    justify-content: flex-end;
    position: relative;
    width: 150px;
    height: 100%;
    padding: 0 10px;
    cursor: pointer;
    .icon-angle-down {
        transition: all .3s;
        font-size: 22px;
    }
    &:hover {
        .icon-angle-down {
            transform-origin: center;
            transform: rotate(-180deg);
        }
        .user-menu {
            padding: 0 20px;
            display: initial;
        }
    }
    .user-menu {
        display: none;
        position: absolute;
        top: 50px;
        width: 100%;
        border: solid $borderWeightColor;
        box-shadow: 0 3px 6px rgba(51, 60, 72, 0.12);
        color: $fontColor;
        background-color: white;
        z-index: 11;
        li {
            height: 40px;
            a {
                color: $fontColor;
                cursor: pointer;
                &:hover {
                    color: $primaryColor;
                }
            }
        }
    }
}
</style>
