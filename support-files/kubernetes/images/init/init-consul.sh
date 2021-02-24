function put_to_consul() {
    file=/data/workspace/etc/bkrepo/$1.yaml
    endpoint=http://$BK_REPO_CONSUL_SERVER_HOST:$BK_REPO_CONSUL_SERVER_PORT/v1/kv/bkrepo-config/$2/data
    result=$(curl -sS -T $file $endpoint)
    if [ "$result" == "true" ]; then
        echo "put $1.yaml to consul success!"
    else
        echo "put $1.yaml to consul failed: $result"
        exit -1
    fi
}

set -e
touch bkrepo.env
/data/workspace/render_tpl -u -p /data/workspace -m bkrepo -e bkrepo.env /data/workspace/templates/*.yaml
services=(auth repository dockerapi generic docker helm maven npm)
for service in ${services[@]};
do
    put_to_consul $service $BK_REPO_SERVICE_PREFIX$service
done
put_to_consul application application
