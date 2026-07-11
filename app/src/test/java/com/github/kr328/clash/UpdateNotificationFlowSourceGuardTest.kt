package com.github.kr328.clash

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateNotificationFlowSourceGuardTest {
    @Test
    fun notificationUsesNonExportedActivityWithoutBroadcastTrampoline() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/AndroidManifest.xml").isFile }
        val checker = File(
            root,
            "app/src/main/java/com/github/kr328/clash/util/AppUpdateChecker.kt",
        ).readText()
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()

        assertTrue(checker.contains("PendingIntent.getActivity("))
        assertTrue(checker.contains("Intent(context, UpdateDownloadActivity::class.java)"))
        assertFalse(checker.contains("PendingIntent.getBroadcast(\n                context,\n                ACTION_DOWNLOAD_REQUEST_CODE"))
        assertTrue(
            Regex(
                """<activity\s+android:name="\.UpdateDownloadActivity"[\s\S]*?android:exported="false""".
                    trimIndent(),
            ).containsMatchIn(manifest),
        )
    }
}
