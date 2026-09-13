package com.networktoolbox

/** User-facing copy for the Drawer-owned information destinations. */
internal object AppInformationPresentation {
    const val aboutTitle = "关于"
    const val appName = "LinkBeacon"
    const val brandSignature = "LinkBeacon by LY"
    const val brandByline = "by LY"
    const val appDescription = "开源网络分析与故障诊断工具箱"
    const val versionTitle = "当前版本"
    const val versionSupport = "当前应用版本"

    const val privacyTitle = "隐私与数据"
    const val localFirstTitle = "本地优先"
    const val localFirstDescription = "诊断数据和检测历史仅保存在设备本地。"
    const val uploadTitle = "数据上传"
    const val uploadDescription = "LinkBeacon 不会将诊断结果和历史记录上传到服务器。"
    const val accountTitle = "无需账号"
    const val accountDescription = "核心功能无需账号即可使用。"

    fun versionValue(versionName: String?): String = AppVersionInfo.formatVersionName(versionName)
}

/** Existing launcher components that can be rendered directly by Compose. */
internal object AboutIconPresentation {
    val foregroundResource: Int = R.drawable.ic_launcher_foreground
    val backgroundResource: Int = R.color.ic_launcher_background
}
