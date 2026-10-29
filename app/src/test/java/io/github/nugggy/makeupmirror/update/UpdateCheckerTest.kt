package io.github.nugggy.makeupmirror.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {
    private fun release(
        tag: String,
        assets: String = """[{"name":"makeup-mirror-${tag.removePrefix("v")}.apk","browser_download_url":"https://example.com/a.apk"}]""",
        extra: String = "",
    ) = """{"tag_name":"$tag","body":"Notes","assets":$assets$extra}"""

    @Test
    fun newerReleaseWithApkIsOffered() {
        val info = UpdateChecker.parseRelease(release("v0.2.0"), "0.1.0")
        assertEquals(Version(0, 2, 0), info?.version)
        assertEquals("https://example.com/a.apk", info?.apkUrl)
        assertEquals("Notes", info?.notes)
    }

    @Test
    fun sameOrOlderReleaseIsIgnored() {
        assertNull(UpdateChecker.parseRelease(release("v0.1.0"), "0.1.0"))
        assertNull(UpdateChecker.parseRelease(release("v0.0.9"), "0.1.0"))
    }

    @Test
    fun releaseWithoutApkIsIgnored() {
        assertNull(UpdateChecker.parseRelease(release("v0.2.0", assets = "[]"), "0.1.0"))
    }

    @Test
    fun malformedTagOrBodyIsIgnored() {
        assertNull(UpdateChecker.parseRelease(release("nightly"), "0.1.0"))
        assertNull(UpdateChecker.parseRelease("not json", "0.1.0"))
        assertNull(UpdateChecker.parseRelease("""{"message":"API rate limit exceeded"}""", "0.1.0"))
    }

    @Test
    fun prereleaseAndDraftAreIgnored() {
        assertNull(UpdateChecker.parseRelease(release("v0.2.0", extra = ""","prerelease":true"""), "0.1.0"))
        assertNull(UpdateChecker.parseRelease(release("v0.2.0", extra = ""","draft":true"""), "0.1.0"))
    }

    @Test
    fun httpErrorReturnsNull() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"rate limited"}"""))
        server.start()
        val checker = UpdateChecker(OkHttpClient(), "0.1.0", server.url("/latest").toString())
        assertEquals(CheckResult.Failed, checker.check())
        server.shutdown()
    }

    @Test
    fun successfulCheckReturnsInfo() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(release("v1.0.0")))
        server.start()
        val checker = UpdateChecker(OkHttpClient(), "0.1.0", server.url("/latest").toString())
        assertEquals(Version(1, 0, 0), (checker.check() as CheckResult.Available).info.version)
        server.shutdown()
    }

    @Test
    fun unreachableServerReturnsNull() = runTest {
        val checker = UpdateChecker(OkHttpClient(), "0.1.0", "http://127.0.0.1:1/latest")
        assertEquals(CheckResult.Failed, checker.check())
    }
}

class UpdateCheckerUpToDateTest {
    @Test
    fun sameVersionIsUpToDate() = kotlinx.coroutines.test.runTest {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"tag_name":"v0.1.0","assets":[{"name":"a.apk","browser_download_url":"https://example.com/a.apk"}]}"""))
        server.start()
        val checker = UpdateChecker(okhttp3.OkHttpClient(), "0.1.0", server.url("/latest").toString())
        org.junit.Assert.assertEquals(CheckResult.UpToDate, checker.check())
        server.shutdown()
    }
}
