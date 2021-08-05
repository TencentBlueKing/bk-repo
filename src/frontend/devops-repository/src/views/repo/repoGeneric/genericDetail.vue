<template>
    <bk-sideslider
        :is-show.sync="detailSlider.show"
        :title="detailSlider.data.name"
        @click.native.stop="() => {}"
        :quick-close="true"
        :width="800">
        <bk-tab class="mt10 ml20 mr20" slot="content" type="unborder-card" :active.sync="tabName">
            <bk-tab-panel name="detailInfo" :label="$t('baseInfo')">
                <div class="detail-info info-area" v-bkloading="{ isLoading: detailSlider.loading }">
                    <div class="flex-center" v-for="key in Object.keys(detailInfoMap)" :key="key">
                        <template v-if="detailSlider.data[key] && (key !== 'size' || !detailSlider.data.folder)">
                            <span>{{ detailInfoMap[key] }}</span>
                            <span class="pl40 break-all">{{ detailSlider.data[key] }}</span>
                        </template>
                    </div>
                </div>
                <div class="detail-info checksums-area" v-if="!detailSlider.folder" v-bkloading="{ isLoading: detailSlider.loading }">
                    <div class="flex-center" v-for="key of ['sha256', 'md5']" :key="key">
                        <span>{{ key.toUpperCase() }}</span>
                        <span class="pl40 break-all">{{ detailSlider.data[key] }}</span>
                    </div>
                </div>
            </bk-tab-panel>
            <bk-tab-panel v-if="!detailSlider.folder" name="metaDate" :label="$t('metaData')">
                <bk-table
                    :data="Object.entries(detailSlider.data.metadata || {})"
                    stripe
                    :outer-border="false"
                    :row-border="false"
                    size="small"
                >
                    <bk-table-column :label="$t('key')" prop="0"></bk-table-column>
                    <bk-table-column :label="$t('value')" prop="1"></bk-table-column>
                </bk-table>
            </bk-tab-panel>
        </bk-tab>
    </bk-sideslider>
</template>
<script>
    export default {
        name: 'genericDetail',
        props: {
            detailSlider: Object
        },
        data () {
            return { tabName: 'detailInfo' }
        },
        computed: {
            detailInfoMap () {
                return {
                    'fullPath': this.$t('path'),
                    'size': this.$t('size'),
                    'createdBy': this.$t('createdBy'),
                    'createdDate': this.$t('createdDate'),
                    'lastModifiedBy': this.$t('lastModifiedBy'),
                    'lastModifiedDate': this.$t('lastModifiedDate')
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.detail-info {
    padding: 15px;
    margin-top: 40px;
    border: 1px solid $borderWeightColor;
    span {
        padding: 10px 0;
        flex: 4;
        &:first-child {
            flex: 1;
            display: flex;
            justify-content: flex-end;
        }
    }
    &.info-area:before {
        content: 'Info';
        position: absolute;
        padding: 0 10px;
        font-weight: 700;
        margin-top: -25px;
        background-color: white
    }
    &.checksums-area:before {
        content: 'Checksums';
        position: absolute;
        padding: 0 10px;
        font-weight: 700;
        margin-top: -25px;
        background-color: white
    }
}
</style>
