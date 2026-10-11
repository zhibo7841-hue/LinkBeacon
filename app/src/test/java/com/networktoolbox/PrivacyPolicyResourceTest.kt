package com.networktoolbox

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class PrivacyPolicyResourceTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }
    private fun strings(locale: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            File(root, "app/src/main/res/$locale/information_strings.xml"),
        )
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index) as org.w3c.dom.Element
            node.getAttribute("name") to node.textContent
        }
    }
    @Test fun fixedPublishedUrlsAreTheOnlyLocalizedTargets() {
        assertEquals("https://zhibo7841-hue.github.io/LinkBeacon/privacy/", strings("values")["info_policy_url"])
        assertEquals("https://zhibo7841-hue.github.io/LinkBeacon/privacy/zh-CN/", strings("values-b+zh+Hans")["info_policy_url"])
    }
    @Test fun policyResourcesHaveFullKeyParity() {
        assertEquals(strings("values").keys, strings("values-b+zh+Hans").keys)
    }
    @Test fun openAndCopyLabelsAreExplicitInBothLanguages() {
        assertEquals("View full privacy policy", strings("values")["info_policy_open"])
        assertEquals("查看完整隐私政策", strings("values-b+zh+Hans")["info_policy_open"])
        assertEquals("Copy policy link", strings("values")["info_policy_copy"])
        assertEquals("复制隐私政策链接", strings("values-b+zh+Hans")["info_policy_copy"])
    }
    @Test fun storageAndSharingAreQualifiedNotAbsoluteLocalOnlyPromises() {
        val en = strings("values")
        val zh = strings("values-b+zh+Hans")
        assertTrue(en.getValue("info_local_first_description").contains("Exported copies"))
        assertTrue(zh.getValue("info_local_first_description").contains("导出副本"))
        assertTrue(en.getValue("info_share_description").contains("may retain or upload"))
        assertTrue(zh.getValue("info_share_description").contains("可能保留或上传"))
        assertFalse(en.values.any { it.contains("All data stays locally") })
        assertFalse(zh.values.any { it.contains("仅保存在设备本地") })
    }
    @Test fun developerUploadsAreSeparateFromNecessaryOffDeviceRequests() {
        for (locale in listOf("values", "values-b+zh+Hans")) {
            val text = strings(locale).getValue("info_upload_description")
            assertTrue(text.contains("LanYun Studio"))
            assertTrue(text.contains("DNS"))
            assertFalse(text.contains("No data collected"))
        }
    }
    @Test fun offlineAndErrorsRemainBundledAndActionable() {
        for (locale in listOf("values", "values-b+zh+Hans")) {
            val values = strings(locale)
            for (key in listOf("info_policy_description", "info_policy_no_browser", "info_policy_open_failed",
                "info_policy_copied", "info_policy_copy_failed")) assertFalse(values.getValue(key).isBlank())
        }
    }
    @Test fun wifiCopyDoesNotPromiseNoLocationRelatedAccessOrNoLateCallbacks() {
        assertTrue(strings("values").getValue("info_wifi_description").contains("location-related"))
        assertTrue(strings("values").getValue("info_wifi_description").contains("does not schedule"))
        assertTrue(strings("values-b+zh+Hans").getValue("info_wifi_description").contains("与位置相关"))
    }
}
