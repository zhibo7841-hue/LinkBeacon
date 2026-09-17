package com.networktoolbox

/** User-facing copy for the Drawer-owned information destinations. */
internal object AppInformationPresentation {
    val aboutTitle = R.string.info_about_title
    const val appName = "LinkBeacon"
    const val brandSignature = "LinkBeacon by LY"
    const val brandByline = "by LY"
    val appDescription = R.string.info_app_description
    val versionTitle = R.string.info_version_title
    val versionSupport = R.string.info_version_support

    val privacyTitle = R.string.info_privacy_title
    val localFirstTitle = R.string.info_local_first_title
    val localFirstDescription = R.string.info_local_first_description
    val uploadTitle = R.string.info_upload_title
    val uploadDescription = R.string.info_upload_description
    val accountTitle = R.string.info_account_title
    val accountDescription = R.string.info_account_description

    fun versionValue(versionName: String?): String = AppVersionInfo.formatVersionName(versionName)
}

/** Existing launcher components that can be rendered directly by Compose. */
internal object AboutIconPresentation {
    val foregroundResource: Int = R.drawable.ic_launcher_foreground
    val backgroundResource: Int = R.color.ic_launcher_background
}
