from __future__ import print_function
import sys
import argparse
import time
import requests
from config import *


def parse_args():
    parser = argparse.ArgumentParser(description="Migration data from Jfrog to BKRepo.")
    parser.add_argument("-o", "--overwrite", action='store_true', dest="overwrite")
    parser.add_argument("-p", "--project", required=True, dest="project")
    parser.add_argument("-e", "--environment", default="prod", choices=["dev", "prod"], dest="env")
    return parser.parse_args()


def remove_prefix(text, prefix):
    return text[len(prefix):] if text.startswith(prefix) else text


def print_log(content, end="\n"):
    if sys.version_info.major == 2:
        print(content, end=end)
        sys.stdout.flush()
    else :
        print(content, end=end, flush=True)


def list_jfrog_node():
    print("retrieve jfrog node...", end="")
    url = jfrog_aql_url()
    auth = jfrog_auth()
    data = jfrog_aql_data()
    response = requests.post(url, data=data, auth=auth)
    assert response.status_code == 200
    nodes_list = response.json()["results"]
    print("count: {}".format(len(nodes_list)))
    return nodes_list


def check_exist(node):
    url = bkrepo_query_url(node["normalized_full_path"])
    auth = (node["created_by"], node["created_by"])
    response = requests.get(url, auth=auth)
    try:
        assert response.status_code == 200
        result = response.json()
        assert result['code'] == 0
        assert result['data']['nodeInfo']['size'] == node['size']
        print("existed.")
        return True
    except AssertionError:
        return False


def fetch_jfrog_file_response(node):
    url = jfrog_download_url(node["path"], node["name"])
    auth = jfrog_auth()
    response = requests.get(url, auth=auth, stream=True)
    assert response.status_code == 200
    return response


def fetch_jfrog_properties(node):
    try:
        url = jfrog_property_url(node["path"], node["name"])
        auth = jfrog_auth()
        response = requests.get(url, auth=auth)
        assert response.status_code == 200
        properties = dict()
        for key, value in response.json()['properties'].items():
            if isinstance(value, list) and len(value) > 0:
                properties[key] = value[0]
        return properties
    except AssertionError:
        return {}


def upload_bkrepo_file(node, jfrog_response, properties):
    url = bkrepo_upload_url(node["normalized_full_path"])
    auth = (node["created_by"], node["created_by"])
    headers = dict()
    for key, value in properties.items():
        headers["X-BKREPO-META-" + key] = value
    headers["X-BKREPO-SIZE"] = str(node["size"])
    headers["X-BKREPO-OVERWRITE"] = "true"
    try:
        response = requests.put(url, auth=auth, headers=headers, data=jfrog_response.iter_content(1024))
        assert response.status_code == 200
        print("success")
    except Exception as exception:
        print("failed: {}".format(exception))
    finally:
        jfrog_response.close()


def migrate_node(node, current, total):
    print("({}/{})migration {}/{}...".format(current, total, node["path"], node["name"]), end="")
    node["normalized_path"] = remove_prefix(node["path"], "bk-custom/{}/".format(args.project))
    node["normalized_full_path"] = "{}/{}".format(node["normalized_path"], node["name"])
    if args.overwrite or not check_exist(node):
        properties = fetch_jfrog_properties(node)
        jfrog_response = fetch_jfrog_file_response(node)
        upload_bkrepo_file(node, jfrog_response, properties)


def migration():
    migration_count = 0
    start_time = time.time()
    jfrog_nodes = list_jfrog_node()
    total = len(jfrog_nodes)
    for index, node in enumerate(jfrog_nodes):
        migrate_node(node, index+1, total)
        migration_count += 1
    end_time = time.time()
    elapsed_time = round(end_time - start_time, 2)
    print("migration successfully, totally: {}, elapsed time: {}s".format(migration_count, elapsed_time))


if __name__ == "__main__":
    args = parse_args()
    config(args)
    migration()
