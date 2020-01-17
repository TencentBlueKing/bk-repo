jfrog_aql_path_template = """
items.find(
    {{
        "repo":"generic-local",
        "type":"file",
        "$or":[
            {{"path":{{"$match": "bk-custom/{project}{path}/*"}}}},
            {{"path":{{"$eq": "bk-custom/{project}{path}"}}}}
        ]
    }}
).include("size","path","name","created_by")
"""

jfrog_aql_node_template = """
items.find(
    {{
        "repo":"generic-local",
        "type":"file",
        "path":"{path}",
        "name":"{name}"
    }}
).include("size","path","name","created_by")
"""

jfrog_config = {
    "dev": {
        "url": "http://dev.artifactory.oa.com/",
        "username": "",
        "password": ""
    },
    "prod": {
        "url": "http://bk.artifactory.oa.com/",
        "username": "",
        "password": ""
    }
}

bkrepo_config = {
    "dev": {
        "url": "http://dev.bkrepo.oa.com/",
        "accessKey": "",
        "secretKey": ""
    },
    "prod": {
        "url": "http://bkrepo.oa.com/",
        "accessKey": "",
        "secretKey": ""
    }
}

env = "prod"
project = None


def config(args):
    global env, project
    env = args.env
    project = args.project


def jfrog_aql_url():
    return jfrog_config[env]["url"] + "api/search/aql"


def jfrog_aql_data(path=None, node=None):
    if node:
        node = node.strip("/")
        path, name = node.rsplit("/", 1)
        return jfrog_aql_node_template.format(path=path, name=name)
    else:
        path = normalized_path(path)
        return jfrog_aql_path_template.format(project=project, path=path)


def normalized_path(path):
    path = path if path else ""
    path = path.strip("/")
    if len(path) > 0:
        path = "/" + path
    return path


def jfrog_property_url(path, name):
    return jfrog_config[env]["url"] + "api/storage/generic-local/{}/{}?properties".format(path, name)


def jfrog_auth():
    return jfrog_config[env]["username"], jfrog_config[env]["password"]


def jfrog_download_url(path, name):
    return jfrog_config[env]["url"] + "generic-local/{}/{}".format(path, name)


def bkrepo_auth():
    return bkrepo_config[env]["accessKey"], bkrepo_config[env]["secretKey"] 

    
def bkrepo_query_url(full_path):
    return bkrepo_config[env]["url"] + 'api/repository/api/node/{}/custom/{}'.format(project, full_path)


def bkrepo_upload_url(full_path):
    return bkrepo_config[env]["url"] + 'generic/{}/custom/{}'.format(project, full_path)


def bkrepo_project_create_url():
    return bkrepo_config[env]["url"] + 'api/repository/api/project'


def bkrepo_repo_create_url():
    return bkrepo_config[env]["url"] + 'api/repository/api/repo'


def bkrepo_project_create_data():
    return {
        "name": project,
        "displayName": project,
        "description": ""
    }


def bkrepo_repo_create_data(name):
    return {
        "projectId": project,
        "name": name,
        "category": "LOCAL",
        "type": "GENERIC",
        "public": True,
        "configuration": {"type": "local"},
        "description": ""
    }