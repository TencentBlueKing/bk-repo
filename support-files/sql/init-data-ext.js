db.account.updateOne(
    { appId: "bcs" },
    {
        $setOnInsert: {
            appId: "bcs",
            locked: "false",
            credentials: [{
                accessKey: "609f9939e6944c5c8a842d88acf85edc",
                secretKey: "e041dd34cd89466648a9b196150f75",
                createdAt: new Date(),
                status: "ENABLE"
            }]
        }
    },
    { upsert: true }
);
