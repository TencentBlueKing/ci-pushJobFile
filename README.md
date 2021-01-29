# Job-文件分发(GITHUB版)

# 配置
插件上架时，需要配置蓝鲸智云相关参数，路径：设置->私有配置
- BK_APP_ID:应用ID，别名bk_app_code，需要使用已添加至蓝鲸ESB接口调用白名单中的应用ID，建议使用蓝鲸开发者中心内的蓝盾调用ESB接口专用应用ID（默认为bk_ci）
- BK_APP_SECRET: 应用ID对应的安全密钥(应用TOKEN)
- ESB_HOST: 蓝鲸ESB网关地址, 例如： http://paas.service.consul:80
- JOB_HOST: 蓝鲸JOB独立地址，例如： http://job.bktencent.com:80

蓝鲸社区版6.0用户可在蓝鲸中控机执行如下代码获取配置信息.
```shell script
source ${CTRL_DIR:-/data/install}/load_env.sh

echo "BK_APP_ID      $BK_CI_APP_CODE"
echo "BK_APP_SECRET  $BK_CI_APP_TOKEN"
echo "ESB_HOST       $BK_PAAS_PRIVATE_URL"
echo "JOB_HOST       $BK_JOB_PUBLIC_URL"

# 参考输出
BK_APP_ID      bk_ci
BK_APP_SECRET  略
ESB_HOST       http://paas.service.consul:80
JOB_HOST       http://job.bktencent.com:80
```
