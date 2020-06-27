package com.tencent.bk.devops.atom.task

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.pojo.AtomResult
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.bk.devops.atom.task.api.ArchiveResourceApi
import com.tencent.bk.devops.atom.task.api.FileTaskStatusEnum
import com.tencent.bk.devops.atom.task.api.FileTypeEnum
import com.tencent.bk.devops.atom.task.pojo.CreateFileTaskRequest
import com.tencent.bk.devops.atom.task.pojo.FastPushFileRequestV2
import com.tencent.bk.devops.atom.task.pojo.FastPushFileSource
import com.tencent.bk.devops.atom.task.pojo.FastPushIpDTO
import com.tencent.bk.devops.atom.task.pojo.InnerJobParam
import com.tencent.bk.devops.atom.task.utils.JobUtils
import com.tencent.bk.devops.atom.task.utils.Keys
import com.tencent.bk.devops.atom.task.utils.OkhttpUtils
import com.tencent.bk.devops.atom.utils.json.JsonUtil
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

@AtomService(paramClass = InnerJobParam::class)
class InnerJobAtom : TaskAtom<InnerJobParam> {

    private var jobHost: String = ""
    private var esbHost: String = ""
    private var appId: String = ""
    private var appSecret: String = ""

    override fun execute(atomContext: AtomContext<InnerJobParam>) {
        val param = atomContext.param
        logger.info("param:${JsonUtil.toJson(param)}")
        val atomResult = atomContext.result
        exceute(param, atomResult)
    }

    companion object {
        val logger = LoggerFactory.getLogger(InnerJobAtom::class.java)
        var archiveResourceApi = ArchiveResourceApi()
    }

    fun exceute(param: InnerJobParam, result: AtomResult) {
        logger.info("开始分发文件(Begin to push file)")
        val srcType = param.srcType
        val jobHost = getConfigValue(Keys.JOB_HOST, param)
        val esbHost = getConfigValue(Keys.ESB_HOST, param)
        val esbCertificateStr = getConfigValue(Keys.ESB_CERTIFICATE, param)
        val appId = getConfigValue(Keys.BK_APP_ID, param)
        val appSecret = getConfigValue(Keys.BK_APP_SECRET, param)
        if (!checkVariable(esbHost, jobHost, appId, appSecret)) {
            throw RuntimeException("请联系管理员，配置插件私有配置(Please contact administrator to config plugin private-configurations)")
        }
        this.jobHost = jobHost!!
        this.esbHost = esbHost!!
        this.appId = appId!!
        this.appSecret = appSecret!!
        // 初始化Https配置
        if (esbCertificateStr != null) {
            OkhttpUtils.addCertificate(esbCertificateStr)
        }

        // 获取build 参数
        when (srcType) {
            "PIPELINE" -> {
                archive(param, result)
            }
            "CUSTOMIZE" -> {
                customizeArchive(param, result)
            }
            "REMOTE" -> {
                remotePush(param, result)
            }
            else -> {
                logger.error("unknown srcType($srcType), terminate!")
            }
        }
    }

    private fun archive(
        param: InnerJobParam,
        result: AtomResult,
        isCustom: Boolean = false
    ) {
        val userId = param.pipelineStartUserName
        val projectId = param.projectName
        val pipelineId = param.pipelineId
        val buildId = param.pipelineBuildId
        val archivePath = param.archivePath
        val artifactoryType = if (isCustom) FileTypeEnum.BK_CUSTOM else FileTypeEnum.BK_ARCHIVE

        logger.info("prepare to match file: $archivePath")
        val taskId = archiveResourceApi.createFileTask(userId, projectId, pipelineId, buildId, CreateFileTaskRequest(appId, appSecret, userId, artifactoryType, archivePath))
        logger.info("Create fileTask, taskId=$taskId")

        val startTime = System.currentTimeMillis()
        var machineIp: String = ""
        var pathStr: String = ""
        // 等待FileTask完成
        var finish = false
        while (!finish) {
            val fileTaskInfo = archiveResourceApi.getFileTaskInfo(userId, projectId, pipelineId, buildId, taskId)
            if (fileTaskInfo == null) {
                result.status = Status.failure
                result.message = "taskId=$taskId not exist"
                return
            }
            if (fileTaskInfo.status == FileTaskStatusEnum.DOWNLOADING.status) {
                logger.info("fileTask downloading")
                Thread.sleep(2000)
            } else if (fileTaskInfo.status == FileTaskStatusEnum.WAITING.status) {
                logger.info("fileTask waiting")
                Thread.sleep(2000)
            } else if (fileTaskInfo.status == FileTaskStatusEnum.DONE.status) {
                logger.info("fileTask finished")
                machineIp = fileTaskInfo.ip
                pathStr = fileTaskInfo.path
                finish = true
            } else if (fileTaskInfo.status == FileTaskStatusEnum.ERROR.status) {
                logger.error("fileTask fail")
                result.status = Status.failure
                result.message = "taskId=$taskId fileTask failed with error"
                return
            } else {
                logger.error("unexpected fileTask status:${fileTaskInfo.status}")
                result.status = Status.failure
                result.message = "taskId=$taskId failed with unexpected fileTask status:${fileTaskInfo.status}"
                return
            }
        }
        logger.info("download and gen temp file time consuming:${System.currentTimeMillis() - startTime}")

        val pathList = pathStr.split(",")
        val count = pathList.size
        logger.info("$count files will be distributed")
        val fileSource = FastPushFileSource(
            file = pathList,
            account = "root",
            ipList = listOf(FastPushIpDTO(machineIp, 0L)),
            customQueryId = listOf()
        )
        logger.info("fileSource:$fileSource")
        try {
            distribute(param, fileSource, result)
        } catch (e: Exception) {
            logger.error(e.localizedMessage)
            result.status = Status.failure
            result.message = e.localizedMessage
            throw RuntimeException("unknown Exception: $e")
        } finally {
            val clearResult = archiveResourceApi.clearFileTask(userId, projectId, pipelineId, buildId, taskId)
            logger.info("clear fileTask:$clearResult")
        }
    }

    private fun customizeArchive(
        param: InnerJobParam,
        result: AtomResult
    ) {
        return archive(param, result, true)
    }

    private fun remotePush(
        param: InnerJobParam,
        result: AtomResult
    ) {
        val sourcePath = param.sourcePath
        val sourceIpListStr = param.sourceIpListStr
        val srcDynamicGroupIdListStr = param.srcDynamicGroupIdListStr
        val sourceAccount = param.sourceAccount
        val sourceIpList = getFastPushIpDTOListFromStr(sourceIpListStr)
        val srcDynamicGroupIdList = getDynamicGroupIdListFromStr(srcDynamicGroupIdListStr)
        if (sourceIpList.isEmpty() && srcDynamicGroupIdList.isEmpty()) {
            throw RuntimeException("At least one of source ipList/dynamicGroupIdList required")
        }
        logger.info("sourcePath:$sourcePath,sourceIpList:$sourceIpList, sourceAccount:$sourceAccount ")
        val fileSource = FastPushFileSource(
            file = listOf(sourcePath),
            account = sourceAccount,
            customQueryId = srcDynamicGroupIdList,
            ipList = sourceIpList
        )
        logger.info("fileSource:$fileSource")
        logger.info("begin to distribute")
        return distribute(param, fileSource, result)
    }

    private fun getFastPushIpDTOListFromStr(ipListStr: String): List<FastPushIpDTO> {
        if (ipListStr.isEmpty()) {
            return listOf()
        }
        val ipList = ipListStr.trim().split(",", ";", "\n")
        return ipList.map { FastPushIpDTO(it.split(":")[1], it.split(":")[0].toLong()) }
    }

    private fun getDynamicGroupIdListFromStr(dynamicGroupIdListStr: String): List<String> {
        var dynamicGroupIdList = listOf<String>()
        if (dynamicGroupIdListStr.isEmpty()) {
            logger.info("dynamicGroupIdListStr is empty")
        } else {
            dynamicGroupIdList = dynamicGroupIdListStr.trim().split(",", "，", ";", "\n").filter(StringUtils::isNotBlank).toList()
        }
        logger.info("dynamicGroupIdList:$dynamicGroupIdList")
        return dynamicGroupIdList
    }

    fun distribute(
        param: InnerJobParam,
        fileSource: FastPushFileSource,
        result: AtomResult
    ) {
        val bizId = param.bizId
        val buildId = param.pipelineBuildId
        val taskId = param.pipelineTaskId
        val targetAccount = param.targetAccount
        val timeout = 0L + (param.timeout ?: 600) * 60_000
        var operator = param.pipelineStartUserName
        val targetPath = param.targetPath
        val lastModifyUser = param.pipelineUpdateUserName
        if (null != lastModifyUser && operator != lastModifyUser) {
            // 以流水线的最后一次修改人身份执行；如果最后一次修改人也没有这个环境的操作权限，这种情况不考虑，有问题联系产品!
            logger.info("operator:$operator, lastModifyUser:$lastModifyUser")
            operator = lastModifyUser
        }
        val targetIpList = getFastPushIpDTOListFromStr(param.targetIpListStr)
        logger.info("targetIpList:$targetIpList")
        val targetDynamicGroupIdList = getDynamicGroupIdListFromStr(param.targetDynamicGroupIdListStr)
        if (targetIpList.isEmpty() && targetDynamicGroupIdList.isEmpty()) {
            throw RuntimeException("At least one of target ipList/dynamicGroupIdList required")
        }

        val fastPushFileReq = FastPushFileRequestV2(
            appCode = this.appId,
            appSecret = this.appSecret,
            username = operator,
            bizId = bizId.toLong(),
            account = targetAccount,
            fileTargetPath = targetPath,
            fileSource = listOf(fileSource),
            customQueryId = targetDynamicGroupIdList,
            ipList = targetIpList,
            callbackUrl = ""
        )
        try {
            val taskInstanceId = JobUtils.fastPushFileV2(fastPushFileReq, this.esbHost)
            logger.info(JobUtils.getDetailUrl(jobHost, bizId, taskInstanceId))
            val startTime = System.currentTimeMillis()

            checkStatus(
                bizId = bizId,
                startTime = startTime,
                maxRunningMills = timeout,
                taskId = taskId,
                taskInstanceId = taskInstanceId,
                operator = operator,
                buildId = buildId,
                esbHost = esbHost
            )

            logger.info(JobUtils.getDetailUrl(jobHost, bizId, taskInstanceId))
        } catch (e: Exception) {
            logger.error("Job API invoke failed", e)
            result.status = Status.failure
            result.message = e.message
            if (e.message != null && e.message!!.contains("permission")) {
                logger.info("====================================================")
                logger.info("看这里！(Attention Please!)")
                logger.info("看这里！(Attention Please!)")
                logger.info("看这里！(Attention Please!)")
                logger.info("====================================================")
                logger.info("Job插件使用流水线最后一次保存人的身份调用作业平台接口，请使用有权限的用户身份保存流水线，权限可到蓝鲸权限中心申请(Job plugin invoke Job API with last modifier of this pipeline, please ensure the last modifier has perssion to access the business on Job, user can apply authorization using Blueking Authorization Center, which is on Blueking Desktop)")
            }
        }
    }

    private fun checkStatus(
        bizId: String,
        startTime: Long,
        maxRunningMills: Long,
        taskInstanceId: Long,
        operator: String,
        buildId: String,
        taskId: String,
        esbHost: String
    ) {
        if (System.currentTimeMillis() - startTime > maxRunningMills) {
            logger.warn("job getTimeout. getTimeout minutes:${maxRunningMills / 60000}")
            throw RuntimeException("job Timeout, executeTime:${System.currentTimeMillis() - startTime}")
        }

        var jobSuccess = true

        while (jobSuccess) {
            Thread.sleep(5000)
            val taskResult = JobUtils.getTaskResult(appId, appSecret, bizId, taskInstanceId, operator, esbHost)
            if (taskResult.isFinish) {
                if (taskResult.success) {
                    logger.info("[$buildId]|SUCCEED|taskInstanceId=$taskId|${taskResult.msg}")
                    jobSuccess = false
                } else {
                    logger.info("[$buildId]|FAIL|taskInstanceId=$taskId|${taskResult.msg}")
                    throw RuntimeException("job execute fail, message:${taskResult.msg}")
                }
            } else {
                logger.info("执行中/Waiting for job:$taskInstanceId", taskId)
            }

            if (System.currentTimeMillis() - startTime > maxRunningMills) {
                logger.error("job execute timeout, exit out")
                throw RuntimeException("job Timeout, executeTime:${System.currentTimeMillis() - startTime}")
            }
        }
        logger.info("Job执行耗时(Time consuming):${System.currentTimeMillis() - startTime}")
    }

    private fun getConfigValue(key: String, param: InnerJobParam): String? {
        val configMap = param.bkSensitiveConfInfo
        if (configMap == null) {
            logger.warn("插件私有配置为空，请补充配置(Plugin private configuration is null, please config it)")
        }
        if (configMap.containsKey(key)) {
            return configMap[key]
        }
        return null
    }

    private fun checkVariable(esbHost: String?, jobHost: String?, appId: String?, appSecret: String?): Boolean {
        if (esbHost.isNullOrBlank()) {
            logger.error("请补充插件 ESB_HOST 配置(Please config plugin private configuration:ESB_HOST)")
            return false
        }
        if (jobHost.isNullOrBlank()) {
            logger.error("请补充插件 JOB_HOST 配置(Please config plugin private configuration:JOB_HOST)")
            return false
        }
        if (appId.isNullOrBlank()) {
            logger.error("请补充插件 BK_APP_ID 配置(Please config plugin private configuration:BK_APP_ID)")
            return false
        }
        if (appSecret.isNullOrBlank()) {
            logger.error("请补充插件 BK_APP_SECRET 配置(Please config plugin private configuration:BK_APP_SECRET)")
            return false
        }
        return true
    }
}