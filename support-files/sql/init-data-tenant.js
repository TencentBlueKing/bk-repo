db.user.updateOne(
    {userId: "admin"},
    {
        $setOnInsert: {
            userId: "admin",
            name: "admin",
            pwd: "5f4dcc3b5aa765d61d8327deb882cf99",
            admin: true,
            locked: false,
            tokens: [],
            roles: [],
            asstUsers: [],
            group: false
        }
    },
    {upsert: true}
);

db.user.updateOne(
    {userId: "system"},
    {
        $setOnInsert: {
            userId: "system",
            name: "system",
            pwd: "5f4dcc3b5aa765d61d8327deb882cf99",
            admin: true,
            locked: false,
            tokens: [],
            roles: [],
            asstUsers: [],
            group: false
        }
    },
    {upsert: true}
);

db.account.updateOne(
    {appId: "bkdevops"},
    {
        $setOnInsert: {
            appId: "bkdevops",
            locked: "false",
            credentials: [{
                accessKey: "18b61c9c-901b-4ea3-89c3-1f74be944b66",
                secretKey: "Us8ZGDXPqk86cwMukYABQqCZLAkM3K",
                createdAt: new Date(),
                status: "ENABLE"
            }]
        }
    },
    {upsert: true}
);

db.project.updateOne(
    {name: "system-blueking"},
    {
        $setOnInsert: {
            name: "system-blueking",
            displayName: "blueking",
            description: "",
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date(),
            projectCode: "blueking",
            tenantId: "system"
        }
    },
    {upsert: true}
);

db.repository.updateOne(
    {
        projectId: "system-blueking",
        name: "generic-local"
    },
    {
        $setOnInsert: {
            projectId: "system-blueking",
            name: "generic-local",
            type: "GENERIC",
            category: "LOCAL",
            public: false,
            description: "generic local repository",
            configuration: "{}",
            display: true,
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {upsert: true}
);

db.repository.updateOne(
    {
        projectId: "system-blueking",
        name: "maven-local"
    },
    {
        $setOnInsert: {
            projectId: "system-blueking",
            name: "maven-local",
            type: "MAVEN",
            category: "LOCAL",
            public: false,
            description: "maven local repository",
            configuration: "{}",
            display: true,
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {upsert: true}
);

db.repository.updateOne(
    {
        projectId: "system-blueking",
        name: "docker-local"
    },
    {
        $setOnInsert: {
            projectId: "system-blueking",
            name: "docker-local",
            type: "DOCKER",
            category: "LOCAL",
            public: false,
            description: "docker local repository",
            configuration: "{}",
            display: true,
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {upsert: true}
);

db.repository.updateOne(
    {
        projectId: "system-blueking",
        name: "npm-local"
    },
    {
        $setOnInsert: {
            projectId: "system-blueking",
            name: "npm-local",
            type: "NPM",
            category: "LOCAL",
            public: false,
            description: "npm local repository",
            configuration: "{}",
            display: true,
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {upsert: true}
);

db.repository.updateOne(
    {
        projectId: "system-blueking",
        name: "pypi-local"
    },
    {
        $setOnInsert: {
            projectId: "system-blueking",
            name: "pypi-local",
            type: "PYPI",
            category: "LOCAL",
            public: false,
            description: "pypi local repository",
            configuration: "{}",
            display: true,
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {upsert: true}
);

db.repository.updateOne(
    {
        projectId: "system-blueking",
        name: "helm-local"
    },
    {
        $setOnInsert: {
            projectId: "system-blueking",
            name: "helm-local",
            type: "HELM",
            category: "LOCAL",
            public: false,
            description: "helm local repository",
            configuration: "{}",
            display: true,
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {upsert: true}
);

db.execution_cluster.updateOne(
    {
        "name": "docker"
    },
    {
        $setOnInsert: {
            name: "docker",
            type: "docker",
            description: "docker executor",
            config: '{"name" : "docker", "host" : "unix:///var/run/docker.sock", "version" : "1.23", "connectTimeout" : 5000, "readTimeout" : 0, "maxTaskCount" : 1, "type" : "docker", "description" : "docker executor"}',
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {
        upsert: true
    }
);

db.scanner.updateOne(
    {
        name: "bkrepo-trivy"
    },
    {
        $setOnInsert: {
            name: "bkrepo-trivy",
            type: "standard",
            version: "0.0.35",
            description: "",
            config: "{\n  \"name\" : \"bkrepo-trivy\",\n  \"image\" : \"ghcr.io/tencentblueking/ci-repoanalysis/bkrepo-trivy:0.0.35\",\n  \"cmd\" : \"/bkrepo-trivy\",\n  \"version\" : \"0.0.35\",\n  \"args\" : [ {\n    \"type\" : \"BOOLEAN\",\n    \"key\" : \"scanSensitive\",\n    \"value\" : \"true\",\n    \"des\" : \"\"\n  } ],\n  \"type\" : \"standard\",\n  \"description\" : \"\",\n  \"rootPath\" : \"/standard\",\n  \"cleanWorkDir\" : true,\n  \"maxScanDurationPerMb\" : 6000,\n  \"supportFileNameExt\" : [],\n  \"supportPackageTypes\" : [ \"DOCKER\" ],\n  \"supportDispatchers\" : [ \"docker\", \"k8s\" ],\n  \"supportScanTypes\" : [ \"SENSITIVE\", \"SECURITY\" ]\n}",
            supportFileNameExt: [],
            supportPackageTypes: ["DOCKER"],
            supportScanTypes: ["SECURITY", "SENSITIVE"],
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {upsert: true}
);

db.scanner.updateOne(
    {
        name: "bkrepo-dependency-check"
    },
    {
        $setOnInsert: {
            name: "bkrepo-dependency-check",
            type: "standard",
            version: "0.0.5",
            description: "dependency-check分析工具",
            config: "{\n  \"name\" : \"bkrepo-dependency-check\",\n  \"image\" : \"ghcr.io/tencentblueking/ci-repoanalysis/bkrepo-dependency-check:0.0.5\",\n  \"cmd\" : \"/bkrepo-dependency-check\",\n  \"version\" : \"0.0.5\",\n  \"args\" : [ {\n    \"type\" : \"BOOLEAN\",\n    \"key\" : \"offline\",\n    \"value\" : \"false\",\n    \"des\" : \"\"\n  } ],\n  \"type\" : \"standard\",\n  \"description\" : \"dependency-check\",\n  \"rootPath\" : \"/standard\",\n  \"cleanWorkDir\" : true,\n  \"maxScanDurationPerMb\" : 6000,\n  \"supportFileNameExt\" : [ \"tar\", \"zip\", \"exe\", \"jar\" ],\n  \"supportPackageTypes\" : [ \"GENERIC\", \"MAVEN\" ],\n  \"supportScanTypes\" : [ \"SECURITY\" ],\n  \"supportDispatchers\" : [ \"docker\", \"k8s\" ],\n  \"memory\" : 34359738368\n}",
            supportFileNameExt: ["tar", "zip", "exe", "jar"],
            supportPackageTypes: ["GENERIC", "MAVEN"],
            supportScanTypes: ["SECURITY"],
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {upsert: true}
);

db.scan_plan.updateOne(
    {
        projectId: "system-blueking",
        name: "ImageScan",
        type: "DOCKER"
    },
    {
        $setOnInsert: {
            projectId: "system-blueking",
            name: "ImageScan",
            type: "DOCKER",
            repoNames: [],
            scanner: "bkrepo-trivy",
            scanTypes: ["SENSITIVE", "SECURITY"],
            description: "",
            scanOnNewArtifact: false,
            rule: "{\n  \"rules\" : [ {\n    \"field\" : \"projectId\",\n    \"value\" : \"blueking\",\n    \"operation\" : \"EQ\"\n  }, {\n    \"field\" : \"type\",\n    \"value\" : \"DOCKER\",\n    \"operation\" : \"EQ\"\n  } ],\n  \"relation\" : \"AND\"\n}",
            scanResultOverview: {},
            scanQuality: {},
            readOnly: false,
            latestScanTaskId: null,
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {upsert: true}
);

db.scan_plan.updateOne(
    {
        projectId: "system-blueking",
        name: "MavenScan",
        type: "MAVEN"
    },
    {
        $setOnInsert: {
            projectId: "system-blueking",
            name: "MavenScan",
            type: "MAVEN",
            repoNames: [],
            scanner: "bkrepo-dependency-check",
            scanTypes: ["SECURITY"],
            description: "",
            scanOnNewArtifact: false,
            rule: "{\n  \"rules\" : [ {\n    \"field\" : \"projectId\",\n    \"value\" : \"blueking\",\n    \"operation\" : \"EQ\"\n  }, {\n    \"field\" : \"type\",\n    \"value\" : \"MAVEN\",\n    \"operation\" : \"EQ\"\n  } ],\n  \"relation\" : \"AND\"\n}",
            scanResultOverview: {},
            scanQuality: {},
            readOnly: false,
            latestScanTaskId: null,
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {upsert: true}
);

db.scan_plan.updateOne(
    {
        projectId: "system-blueking",
        name: "GenericScan",
        type: "MAVEN"
    },
    {
        $setOnInsert: {
            projectId: "system-blueking",
            name: "GenericScan",
            type: "GENERIC",
            repoNames: [],
            scanner: "bkrepo-dependency-check",
            scanTypes: ["SECURITY"],
            description: "",
            scanOnNewArtifact: false,
            rule: "{\n  \"rules\" : [ {\n    \"field\" : \"projectId\",\n    \"value\" : \"blueking\",\n    \"operation\" : \"EQ\"\n  }, {\n    \"field\" : \"type\",\n    \"value\" : \"GENERIC\",\n    \"operation\" : \"EQ\"\n  } ],\n  \"relation\" : \"AND\"\n}",
            scanResultOverview: {},
            scanQuality: {},
            readOnly: false,
            latestScanTaskId: null,
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    {upsert: true}
);
