<template>
    <bk-sideslider
        :is-show.sync="detailSlider.show"
        :title="detailSlider.data.name"
        @click.native.stop="() => {}"
        :quick-close="true"
        :width="800">
        <bk-tab class="mt10 ml20 mr20" slot="content" type="unborder-card">
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
