<template>
    <div class="repo-docker-detail flex-column">
        <div class="docker-base-info flex-align-center">
            <img v-if="docker.logoUrl" :src="docker.logoUrl" width="100" height="100" />
            <icon v-else size="100" name="default-docker" />
            <div class="ml20 docker-title flex-column">
                <span class="mb10 title" :title="docker.name">{{ docker.name }}</span>
                <span>{{ `${docker.lastModifiedBy} ${$t('modifiedOn')} ${new Date(docker.lastModifiedDate).toLocaleString()}` }}</span>
                <div class="mt10 subtitle" :title="docker.description">{{ docker.description || $t('noDescription') }}</div>
            </div>
            <div class="docker-download">
                <i class="mr5 devops-icon icon-download"></i>
                <span>{{ docker.downloadCount }}</span>
            </div>
        </div>
        <div class="mt30 docker-tab">
            <bk-tab class="docker-tab-main" type="unborder-card">
                <bk-tab-panel name="dockerDescription" :label="$t('dockerInfo')">
                    <article class="docker-description">
                        <section>
                            <header class="docker-description-header">bkci base dockerimage</header>
                        </section>
                        <section>
                            <header class="docker-description-header">{{ $t('dockerBaseInfo1') }}</header>
                            <code-area :code-list="[`docker pull ${$route.query.name}/${$route.query.docker}:latest`]"></code-area>
                        </section>
                        <section>
                            <header class="docker-description-header">{{ $t('dockerBaseInfo2') }}</header>
                            <div class="docker-description-tip">{{ $t('dockerBaseInfo3') }}</div>
                            <code-area :code-list="[`docker pull ${$route.query.name}/${$route.query.docker}:latest`]"></code-area>
                        </section>
                    </article>
                </bk-tab-panel>
                <bk-tab-panel name="dockerTag" label="Tag">
                    <div class="mb20 flex-align-center">
                        <bk-input
                            class="docker-tag-search"
                            v-model="tagNameInput"
                            :placeholder="$t('pleaseInput') + 'tag' + $t('name')"
                            clearable>
                        </bk-input>
                    </div>
                    <bk-table
                        class="docker-tag-table"
                        height="calc(100% - 34px)"
                        :data="filterTagList"
                        :outer-border="false"
                        :row-border="false"
                        size="small"
                        @row-click="toDockerTagDetail"
                        :pagination="pagination"
                        @page-change="current => handlerPaginationChange({ current })"
                        @page-limit-change="limit => handlerPaginationChange({ limit })"
                    >
                        <bk-table-column :label="$t('name')" prop="tag"></bk-table-column>
                        <bk-table-column :label="$t('artiStatus')">
                            <template v-if="props.row.status" slot-scope="props">
                                <span class="mr5 repo-generic-tag" v-for="tag in props.row.status.split(',')"
                                    :key="props.row.tag + tag">{{ tag }}</span>
                            </template>
                        </bk-table-column>
                        <bk-table-column :label="$t('size')">
                            <template slot-scope="props">
                                {{ convertFileSize(props.row.size) }}
                            </template>
                        </bk-table-column>
                        <bk-table-column :label="$t('downloadCount')" prop="downloadCount"></bk-table-column>
                        <bk-table-column :label="$t('lastModifiedBy')" prop="lastModifiedBy"></bk-table-column>
                        <bk-table-column :label="$t('lastModifiedDate')">
                            <template slot-scope="props">
                                {{ new Date(props.row.lastModifiedDate).toLocaleString() }}
                            </template>
                        </bk-table-column>
                        <bk-table-column :label="$t('operation')" width="150">
                            <template slot-scope="props">
                                <!-- <i class="devops-icon icon-arrows-up mr20" @click="showRepoConfig(props.row)"></i> -->
                                <i class="devops-icon icon-delete hover-btn" @click.stop="deleteTag(props.row)"></i>
                            </template>
                        </bk-table-column>
                    </bk-table>
                </bk-tab-panel>
            </bk-tab>
        </div>
    </div>
</template>
<script>
    import CodeArea from '@/components/CodeArea'
    import { convertFileSize } from '@/utils'
    export default {
        name: 'dockerDetail',
        components: { CodeArea },
        props: {
            docker: Object,
            dockerTagList: {
                type: Array,
                default: []
            }
        },
        data () {
            return {
                convertFileSize,
                tagNameInput: '',
                pagination: {
                    count: this.dockerTagList.length,
                    current: 1,
                    limit: 10,
                    'limit-list': [10, 20, 40]
                }
            }
        },
        computed: {
            filterTagList () {
                const start = (this.pagination.current - 1) * this.pagination.limit
                this.pagination.count = this.dockerTagList.length
                return this.dockerTagList
                    .filter(v => v.tag.includes(this.tagNameInput))
                    .slice(start, this.pagination.limit)
            }
        },
        watch: {
            dockerTagList (list) {
                if (list.length < (this.pagination.current - 1) * this.pagination.limit) {
                    this.pagination.current--
                }
            }
        },
        methods: {
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
            },
            toDockerTagDetail (tag) {
                this.$emit('show-tag-detail', tag)
            },
            deleteTag (row) {
                this.$emit('delete-tag', row.tag)
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.repo-docker-detail {
    height: 100%;
    .docker-base-info {
        height: 100px;
        .docker-title {
            .title {
                font-size: 20px;
                color: $fontBoldColor
            }
            .title, .subtitle {
                max-width: 500px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }
        }
        .docker-download {
            margin-left: 80px;
        }
    }
    .docker-tab {
        flex: 1;
        .docker-tab-main {
            height: 100%;
            /deep/ .bk-tab-section {
                height: calc(100% - 40px);
                .bk-tab-content {
                    height: 100%;
                }
            }
            .docker-tag-search {
                width: 250px;
            }
            .docker-tag-table {
                margin-bottom: -20px;
            }
            .docker-description {
                .docker-description-header {
                    margin: 10px 0 20px;
                    font-size: 16px;
                    line-height: 2;
                    border-bottom: 1px solid;
                }
                .docker-description-tip {
                    margin-bottom: 10px;
                    font-size: 12px;
                    padding-left: 20px;
                    line-height: 1.5;
                }
            }
        }
    }
}
</style>
