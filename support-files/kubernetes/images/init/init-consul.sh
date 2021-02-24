touch bkrepo.env
/data/workspace/render_tpl -u -p /data/workspace -m bkrepo -e bkrepo.env /data/workspace/templates/*.yaml
services=(auth repository dockerapi generic docker helm maven npm)
for var in ${services[@]};
do
    service=$BK_REPO_SERVICE_PREFIX$var
    curl -T /data/workspace/etc/bkrepo/$var.yaml http://$BK_REPO_CONSUL_SERVER_HOST:$BK_REPO_CONSUL_SERVER_PORT/v1/kv/bkrepo-config/$service/data
done
curl -T /data/workspace/etc/bkrepo/application.yaml http://$BK_REPO_CONSUL_SERVER_HOST:$BK_REPO_CONSUL_SERVER_PORT/v1/kv/bkrepo-config/application/data
