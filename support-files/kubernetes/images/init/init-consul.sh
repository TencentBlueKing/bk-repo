/data/workspace/render_tpl -u -m bkrepo -p /data /data/workspace/templates/*.yaml
services=(application auth repository dockerapi generic docker helm maven npm)
for var in ${services[@]};
do
    service=$BK_REPO_SERVICE_PREFIX$var
    echo "properties $service start..."
    curl -T /data/etc/bkrepo/$var.yaml http://$BK_REPO_CONSUL_SERVER:8500/v1/kv/bkrepo-config/$service/data
    echo "properties $service finish..."
done