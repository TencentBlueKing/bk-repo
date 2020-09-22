<template>
    <article class="docker-guide-container">
        <section class="mb20" v-for="section in article" :key="section.title">
            <h2 class="mb10 section-header">{{ section.title }}</h2>
            <div class="section-main">
                <div class="sub-section flex-column" v-for="block in section.main" :key="block.subTitle">
                    <span class="mb10">{{ block.subTitle }}</span>
                    <code-area :code-list="block.codeList"></code-area>
                </div>
            </div>
        </section>
    </article>
</template>
<script>
    import CodeArea from '@/components/CodeArea'
    export default {
        name: 'dockerGuide',
        components: { CodeArea },
        data () {
            return {
                article: [
                    {
                        title: '设置凭证',
                        main: [
                            {
                                subTitle: '配置个人凭证',
                                codeList: [`docker login -u <账号> ${location.host}`]
                            }
                        ]
                    },
                    {
                        title: '推送',
                        main: [
                            {
                                subTitle: '1、给本地的镜像打标签',
                                codeList: [`docker tag <LOCAL_IMAGE_TAG> ${location.host}/${this.$route.params.projectId}/${this.$route.query.name}/<PACKAGE>`]
                            },
                            {
                                subTitle: '2、推送您的docker 镜像',
                                codeList: [`docker push ${location.host}/${this.$route.params.projectId}/${this.$route.query.name}/<PACKAGE>`]
                            }
                        ]
                    },
                    {
                        title: '下载',
                        main: [
                            {
                                codeList: [`docker pull ${location.host}/${this.$route.params.projectId}/${this.$route.query.name}/<PACKAGE>`]
                            }
                        ]
                    }
                ]
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.docker-guide-container {
    padding: 20px;
    .section-header {
        padding: 10px 20px;
        background-color: #f1f2f3;
    }
    .section-main {
        padding: 20px;
        border: 2px dashed $borderWeightColor;
        border-radius: 5px;
        .sub-section {
            & + .sub-section {
                margin-top: 20px;
            }
        }
    }
}
</style>
