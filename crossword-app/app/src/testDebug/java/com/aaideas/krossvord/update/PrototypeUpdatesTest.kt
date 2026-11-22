package com.aaideas.krossvord.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrototypeUpdatesTest {
    private fun release(tag: String, asset: String = "krossvord.apk") = """
        {"tag_name":"$tag","name":"Krossvord","assets":[
          {"name":"other.txt","browser_download_url":"https://x/other.txt"},
          {"name":"$asset","browser_download_url":"https://github.com/o/r/releases/download/$tag/$asset"}
        ]}
    """

    @Test fun readsBuildNumberAndApkUrl() {
        val r = PrototypeUpdates.parseLatest(release("krossvord-42"))!!
        assertEquals(42, r.build)
        assertEquals("0.1.42", r.versionName)
        assertEquals("https://github.com/o/r/releases/download/krossvord-42/krossvord.apk", r.apkUrl)
    }

    @Test fun ignoresOtherReleases() {
        assertNull(PrototypeUpdates.parseLatest(release("v1.0")))
        assertNull(PrototypeUpdates.parseLatest(release("krossvord-abc")))
        assertNull(PrototypeUpdates.parseLatest(release("krossvord-5", asset = "app.apk")))
        assertNull(PrototypeUpdates.parseLatest("{\"message\":\"Not Found\"}"))
        assertNull(PrototypeUpdates.parseLatest("not json"))
    }
}
