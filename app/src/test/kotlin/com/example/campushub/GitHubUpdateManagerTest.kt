package com.example.campushub

import com.example.campushub.data.update.GitHubUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateManagerTest {

  @Test
  fun isNewerVersion_handlesSemVerPrefixesAndPreReleases() {
    assertTrue(GitHubUpdateManager.isNewerVersion("v0.4.0", "0.3.0"))
    assertTrue(GitHubUpdateManager.isNewerVersion("v0.10.0", "0.3.0"))
    assertTrue(GitHubUpdateManager.isNewerVersion("v0.3.1", "0.3.0"))
    assertTrue(GitHubUpdateManager.isNewerVersion("v0.4.0", "0.4.0-rc.1"))
    assertTrue(GitHubUpdateManager.isNewerVersion("v0.4.0", "dev"))

    assertFalse(GitHubUpdateManager.isNewerVersion("v0.3.0", "0.3.0"))
    assertFalse(GitHubUpdateManager.isNewerVersion("v0.2.0", "0.3.0"))
    assertFalse(GitHubUpdateManager.isNewerVersion("v0.4.0-beta.1", "0.4.0"))
    assertFalse(GitHubUpdateManager.isNewerVersion("v0.3.0", "1.0"))
  }

  @Test
  fun parseLatestRelease_selectsApkAndSha256AssetsAndSkipsDrafts() {
    val releaseJson =
      """
      {
        "tag_name": "v0.4.0",
        "name": "TJ Hub v0.4.0",
        "draft": false,
        "html_url": "https://github.com/Larmingral/Android_Tongji_hub/releases/tag/v0.4.0",
        "body": "1. 修复考试安排提取\n2. 新增应用内检查更新",
        "assets": [
          {
            "name": "TJ-Hub-v0.4.0.apk.sha256",
            "size": 90,
            "browser_download_url": "https://github.com/Larmingral/Android_Tongji_hub/releases/download/v0.4.0/TJ-Hub-v0.4.0.apk.sha256"
          },
          {
            "name": "TJ-Hub-v0.4.0.apk",
            "size": 12582912,
            "browser_download_url": "https://github.com/Larmingral/Android_Tongji_hub/releases/download/v0.4.0/TJ-Hub-v0.4.0.apk"
          }
        ]
      }
      """.trimIndent()

    val parsed = GitHubUpdateManager.parseLatestRelease(releaseJson)
    assertNotNull(parsed)
    assertEquals("v0.4.0", parsed!!.tagName)
    assertEquals("0.4.0", parsed.normalizedVersion)
    assertEquals("TJ-Hub-v0.4.0.apk", parsed.apkName)
    assertEquals(12582912L, parsed.apkSizeBytes)
    assertTrue(parsed.apkDownloadUrl.endsWith("TJ-Hub-v0.4.0.apk"))
    assertTrue(parsed.sha256DownloadUrl?.endsWith(".sha256") == true)

    val listJson =
      """
      [
        {"tag_name": "v0.5.0-draft", "draft": true, "assets": []},
        $releaseJson
      ]
      """.trimIndent()
    val fromList = GitHubUpdateManager.parseReleaseFromList(listJson)
    assertEquals("v0.4.0", fromList?.tagName)

    val draftOnly = GitHubUpdateManager.parseLatestRelease("""{"tag_name":"v0.5.0","draft":true,"assets":[]}""")
    assertNull(draftOnly)
  }

  @Test
  fun parseSha256Text_extracts64HexDigest() {
    val line = "a3f1b2c3d4e5f60718293a4b5c6d7e8f90112233445566778899aabbccddeeff  TJ-Hub-v0.4.0.apk\n"
    assertEquals(
      "a3f1b2c3d4e5f60718293a4b5c6d7e8f90112233445566778899aabbccddeeff",
      GitHubUpdateManager.parseSha256Text(line),
    )
    assertNull(GitHubUpdateManager.parseSha256Text("invalid checksum"))
  }
}
