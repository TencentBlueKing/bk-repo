/data/workspace/render_tpl -u -m bkrepo -p /data /data/workspace/templates/*.yaml
backends=(application auth repository generic)
for var in ${backends[@]};
do
    echo "properties $var start..."
    curl -T /data/etc/bkrepo/$var.yaml http://$BK_REPO_CONSUL_SERVER:8500/v1/kv/bkrepo-config/$var/data
    echo "properties $var finish..."
done