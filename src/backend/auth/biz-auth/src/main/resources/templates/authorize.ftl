<!DOCTYPE html>
<html>
<head>
    <title>制品库 | 腾讯蓝鲸智云</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f4f4f4;
        }

        .oauth-container {
            width: 400px;
            margin: 0 auto;
            padding: 20px;
            background-color: #fff;
            border: 1px solid #ccc;
            border-radius: 5px;
            box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
        }

        .oauth-container h2 {
            text-align: center;
        }

        .oauth-container p {
            text-align: center;
            margin-bottom: 20px;
        }

        .oauth-container button {
            width: 100%;
            padding: 10px;
            background-color: #4CAF50;
            color: #fff;
            border: none;
            border-radius: 3px;
            cursor: pointer;
        }

        .oauth-container button:hover {
            background-color: #45a049;
        }
    </style>
</head>
<body>
<div class="oauth-container">
    <h2>授权</h2>
    <p>请授权应用${appId}申请访问您(${userId})的帐户:</p>
    <button onclick="authorize()">确认授权</button>
</div>

<script>
    function authorize() {
        window.location.href="${redirectUrl}"
    }
</script>
</body>
</html>
