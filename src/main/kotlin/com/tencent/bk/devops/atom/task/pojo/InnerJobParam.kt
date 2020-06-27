package com.tencent.bk.devops.atom.task.pojo

import com.tencent.bk.devops.atom.pojo.AtomBaseParam
import lombok.Data
import lombok.EqualsAndHashCode

@Data
@EqualsAndHashCode(callSuper = true)
class InnerJobParam : AtomBaseParam() {
	val bizId: String = ""
	val srcType: String = ""
	val sourceIpListStr: String = ""
	val srcDynamicGroupIdListStr: String = ""
	val sourceAccount: String = ""
	val sourcePath: String = ""
	val archivePath: String = ""
	val targetIpListStr: String = ""
	val targetDynamicGroupIdListStr: String = ""
	val targetAccount: String = ""
	val targetPath: String = ""
	val timeout: Int? = 600
}