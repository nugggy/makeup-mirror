package io.github.nugggy.makeupmirror.styles

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LookAssetsTest {
    private val looks = LookLibrary.parse(File("src/main/assets/styles/looks.json").readText())

    @Test
    fun everyLookImageExists() {
        looks.forEach { assertTrue("missing ${it.image}", File("src/main/assets/styles/${it.image}").isFile) }
    }

    @Test
    fun everyImageIsCreditedInSources() {
        val sources = File("../SOURCES.md").readText()
        looks.forEach { assertTrue("${it.image} not in SOURCES.md", it.image in sources) }
    }
}
