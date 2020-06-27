package com.tencent.bk.devops.atom.task.utils

import java.io.File


object FileUtils {

    //docker 指向docket母机挂载的软链
    fun getDockerWorkspraceFilePath(pipelineId: String, vmseqId: String): String {
        val poolNo: Int = Integer.parseInt(System.getenv("pool_no") ?: "1")
        val workspaceFilePath = "/data/bkee/public/ci/docker/workspace/$pipelineId/$vmseqId"
        if (poolNo > 1) {
            return workspaceFilePath + "_$poolNo"
        } else {
            return workspaceFilePath
        }
    }

    /**
     *  容器挂载路径
     *  容器挂载默认路径： /data/bkee/public/ci/docker/workspace 指向 母机路径：  /data/bkee/public/ci/docker/workspace/${pipeline}/${vmseqId}
     *  往容器挂宅路径下操作文件，母机路径将指向母机挂宅路径。
     *  如向容器 /data/bkee/public/ci/docker/workspace/$buildId 添加文件。则母机路径 /data/bkee/public/ci/docker/workspace/${pipeline}/${vmseqId}/${buildId} 下将有对应文件。
     *  容器内的路径，容器销毁后即不存在。母机的若不删除，一直存在。
     */
    fun getContainerLink(buildId: String): String {
        return "/data/devops/workspace${getContainerTmpLink(buildId)}"
    }

    fun getContainerTmpLink(buildId: String): String {
        return "/$buildId/jobPush"
    }

    /**
     *  拼接母机存放路径
     *  规则 /母机挂宅路径/$buildId/$fileName
     */
    fun getDockerSavePath(pipelineId: String, vmseqId: String, buildId: String): String {
        val dockerWorkSpace = getDockerWorkspraceFilePath(pipelineId, vmseqId)
        val containerLink = getContainerTmpLink(buildId)
        return dockerWorkSpace + containerLink
    }


    /**
     * 按指定路径，在容器内创建文件
     */
    fun createDockerContainerFile(buildId: String, fileName: String): File {
        return File(getContainerLink(buildId), fileName)
    }
}