package io.github.nugggy.makeupmirror.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun parsesWithAndWithoutPrefix() {
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(0, 10, 0), Version.parse("0.10.0"))
    }

    @Test
    fun rejectsMalformed() {
        assertNull(Version.parse("1.2"))
        assertNull(Version.parse("v1.2.3-beta"))
        assertNull(Version.parse("latest"))
    }

    @Test
    fun comparesNumericallyNotAlphabetically() {
        assertTrue(Version.parse("0.10.0")!! > Version.parse("0.9.9")!!)
        assertTrue(Version.parse("1.0.0")!! > Version.parse("0.99.99")!!)
        assertEquals(0, Version(1, 0, 0).compareTo(Version(1, 0, 0)))
    }
}
