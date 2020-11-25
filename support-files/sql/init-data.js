db.user.updateOne(
    { userId: "admin" },
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
    { upsert: true }
);

db.account.updateOne(
    { appId: "bkdevops" },
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
    { upsert: true }
);

db.project.updateOne(
    { name: "bkee" },
    {
        $setOnInsert: {
            name: "bkee",
            displayName: "bkee",
            description: "",
            createdBy: "admin",
            createdDate: new Date(),
            lastModifiedBy: "admin",
            lastModifiedDate: new Date()
        }
    },
    { upsert: true }
);

db.repository.updateOne(
    {
        projectId: "bkee",
        name: "generic-local"
    },
    {
        $setOnInsert: {
            projectId: "bkee",
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
    { upsert: true }
);

db.repository.updateOne(
    {
        projectId: "bkee",
        name: "maven-local"
    },
    {
        $setOnInsert: {
            projectId: "bkee",
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
    { upsert: true }
);

db.repository.updateOne(
    {
        projectId: "bkee",
        name: "docker-local"
    },
    {
        $setOnInsert: {
            projectId: "bkee",
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
    { upsert: true }
);

db.repository.updateOne(
    {
        projectId: "bkee",
        name: "npm-local"
    },
    {
        $setOnInsert: {
            projectId: "bkee",
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
    { upsert: true }
);

db.repository.updateOne(
    {
        projectId: "bkee",
        name: "pypi-local"
    },
    {
        $setOnInsert: {
            projectId: "bkee",
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
    { upsert: true }
);

db.repository.updateOne(
    {
        projectId: "bkee",
        name: "helm-local"
    },
    {
        $setOnInsert: {
            projectId: "bkee",
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
    { upsert: true }
);
