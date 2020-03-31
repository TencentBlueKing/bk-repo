jfrog_aql_path_template = """
items.find(
    {{
        "repo":"generic-local",
        "type":"file",
        "$or":[
            {{"path":{{"$match": "{repo}/{project}{path}/*"}}}},
            {{"path":{{"$eq": "{repo}/{project}{path}"}}}}
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
        "accessKey": "cb475f0b-0b2e-4b9b-9ab2-977385d37e45",
        "secretKey": "6uS19b87YIU6vWNHiMoeCla6x3tsx8"
    },
    "prod": {
        "url": "http://bkrepo.oa.com/",
        "accessKey": "cb475f0b0b2e4b9b9ab2977385d37e45",
        "secretKey": "6uS19b87YIU6vWNHiMoeCla6x3tsx8"
    }
}

env = "prod"
project = None
bkrepo_repo = "custom"
jfrog_repo = "bk-custom"


def config(args):
    global env, project, bkrepo_repo, jfrog_repo
    env = args.env
    project = args.project
    if args.repository == "custom":
        jfrog_repo = "bk-custom"
        bkrepo_repo = "custom"
    elif args.repository == "pipeline":
        jfrog_repo = "bk-archive"
        bkrepo_repo = "pipeline"

def jfrog_repo():
    return jfrog_repo

def bkrepo_repo():
    return bkrepo_repo

def jfrog_aql_url():
    return jfrog_config[env]["url"] + "api/search/aql"


def jfrog_aql_data(path=None, node=None):
    if node:
        node = node.strip("/")
        path, name = node.rsplit("/", 1)
        return jfrog_aql_node_template.format(path=path, name=name)
    else:
        path = normalized_path(path)
        return jfrog_aql_path_template.format(repo=jfrog_repo, project=project, path=path)


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
    return bkrepo_config[env]["url"] + 'repository/api/node/{}/{}/{}'.format(project, bkrepo_repo, full_path)


def bkrepo_upload_url(full_path):
    return bkrepo_config[env]["url"] + 'generic/{}/{}/{}'.format(project, bkrepo_repo, full_path)


def bkrepo_project_create_url():
    return bkrepo_config[env]["url"] + 'repository/api/project'


def bkrepo_repo_create_url():
    return bkrepo_config[env]["url"] + 'repository/api/repo'


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
        "public": False,
        "configuration": {"type": "local"},
        "description": ""
    }