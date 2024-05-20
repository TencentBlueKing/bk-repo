<template>
    <div class="empty-guide-container flex-center">
        <div style="flex-wrap:wrap; margin-top:-120px;">
            <Icon class="empty-guide-common" name="empty-guide" size="200" />
            <span class="empty-guide-tip flex-center">{{$t('emptyGuide1')}}</span>
            <span class="mt5 empty-guide-tip2 flex-center">{{$t('emptyGuide2')}}</span>
            <bk-button class="mt20 empty-guide-common" @click="onClickUseGuide">
                <span class="flex-align-center">
                    <Icon class="mr5" name="hand-guide" size="16" />
                    {{$t('guide')}}
                </span>
            </bk-button>
        </div>
        <useGuide ref="useGuideRef"></useGuide>
    </div>
</template>
<script>
    import useGuide from '@repository/views/repoCommon/useGuide'
    export default {
        name: 'emptyGuide',
        components: { useGuide },
        computed: {
           
        },
        methods: {
            onClickUseGuide () {
                this.$refs.useGuideRef.setData({
                    show: true,
                    loading: false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.empty-guide-container {
    .empty-guide-tip {
        font-size: 16px;
        font-weight: bold;
        color: var(--fontPrimaryColor);
    }
    .empty-guide-tip2 {
        font-size: 16px;
        font-weight: bold;
        color: var(--fontSubsidiaryColor);
    }
    .empty-guide-common{
        display: block;
        margin: auto;
    }
}
</style>
