<template>
    <span class="repo-tag STOP" data-name="禁止使用"
        v-bk-tooltips="{ content: tooltipContent, placements: ['top'] }"></span>
</template>
<script>
    import { mapState } from 'vuex'
    export default {
        name: 'forbidTag',
        props: {
            forbidUser: String,
            forbidType: String
        },
        computed: {
            ...mapState(['userList']),
            tooltipContent () {
                switch (this.forbidType) {
                    case 'SCANNING':
                        return '制品正在扫描中'
                    case 'QUALITY_UNPASS':
                        return '制品扫描质量规则未通过'
                    case 'MANUAL':
                        return `${this.userList[this.forbidUser]?.name || this.forbidUser} 手动禁止`
                    default:
                        return ''
                }
            }
        }
    }
</script>
