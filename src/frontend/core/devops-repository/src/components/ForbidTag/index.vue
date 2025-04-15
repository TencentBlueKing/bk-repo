<template>
    <span class="repo-tag STOP" :data-name="$t('forbiddenUse')"
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
                        return this.$t('forbidTip1')
                    case 'QUALITY_UNPASS':
                        return this.$t('forbidTip2')
                    case 'MANUAL':
                        return `${this.userList[this.forbidUser]?.name || this.forbidUser}` + this.$t('manualBan')
                    default:
                        return ''
                }
            }
        }
    }
</script>
