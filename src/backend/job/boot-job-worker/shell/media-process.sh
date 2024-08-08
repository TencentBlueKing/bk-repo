#!/bin/bash
get_field() {
    local field_name=$1
    field_value=$(echo $DEVOPS_SCHEDULE_JOB_PARAMETERS| jq -r ".$field_name")
    if [ -z "$field_value" ]; then
      echo "Error: Field '$field_name' not found in input file"
      exit 1
    fi
    echo "$field_value"
}

echo "1. 获取参数"
inputUrl=$(get_field inputUrl)
callbackUrl=$(get_field callbackUrl)
inputFileName=$(get_field inputFileName)
scale=$(get_field scale)
videoCodec=$(get_field videoCodec)
outputFileName=$(get_field outputFileName)

echo "2. 下载待转码文件 - $inputFileName"
# 使用 -s 参数静默执行，-w "%{http_code}" 输出HTTP状态码
http_status=$(curl -s -w "%{http_code}" -o $inputFileName $inputUrl)
if [ $http_status -ne 200 ];then
  echo "文件下载失败[$http_status]，Url: $inputUrl"
  exit 1
fi
ls -l

echo "3. 开始转码 - $inputFileName > $outputFileName"
echo "ffmpeg -i $inputFileName -vf scale=$scale -c:a copy -c:v $videoCodec $outputFileName"
ffmpeg -i $inputFileName -vf scale=$scale -c:a copy -c:v $videoCodec $outputFileName
if [ $? -ne 0 ];then
  echo "转码失败"
  exit 1
fi

echo "4. 上传转码后的文件 - $outputFileName"
http_status=$(curl -s -w "%{http_code}" -X PUT -T $outputFileName "$callbackUrl")
if [ $http_status -ne 200 ];then
  echo 文件上传失败[$http_status],Url: $callbackUrl
  exit 1
fi

echo "转码完成 - $inputFileName"