package dev.skyglint.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/** A source dependency guard, not a claim that arbitrary blocking code can be statically proven safe. */
class CoreBoundaryTest {
    @Test
    fun `production core has no platform adapter preview or IO dependencies`() {
        val root = Path.of(System.getProperty("skyglint.projectRoot", "."))
            .resolve("src/main/kotlin/dev/skyglint/core")
        val sources = Files.walk(root).use { paths -> paths.filter { it.toString().endsWith(".kt") }.toList() }
        assertTrue(sources.isNotEmpty(), "No production core sources found at $root")
        val forbidden = Regex(
            """\b(?:dev\.skyglint\.(?!core\b)[A-Za-z_]\w*|net\.(?:minecraft|fabricmc|hypixel)|com\.mojang|org\.lwjgl|java\.(?:io|net)|java\.nio\.file|kotlin\.io)(?=\.|\b)""",
        )
        val violations = sources.flatMap { path ->
            val code = withoutCommentsAndLiterals(path.readText())
            forbidden.findAll(code).map { match ->
                val line = code.take(match.range.first).count { it == '\n' } + 1
                "${root.relativize(path)}:$line: ${match.value}"
            }.toList()
        }
        assertTrue(violations.isEmpty(), "Core must depend only on its domain and non-I/O JVM values:\n${violations.joinToString("\n")}")
    }

    // Preserve newlines so diagnostics point at the original source. Kotlin block comments nest.
    private fun withoutCommentsAndLiterals(source: String): String {
        val result = source.toCharArray()
        var index = 0
        fun mask(from: Int, until: Int) {
            for (offset in from until until) if (result[offset] != '\n') result[offset] = ' '
        }
        while (index < source.length) {
            val start = index
            when {
                source.startsWith("//", index) -> {
                    index = source.indexOf('\n', index).takeIf { it >= 0 } ?: source.length
                    mask(start, index)
                }
                source.startsWith("/*", index) -> {
                    index += 2
                    var depth = 1
                    while (index < source.length && depth > 0) {
                        when {
                            source.startsWith("/*", index) -> { depth++; index += 2 }
                            source.startsWith("*/", index) -> { depth--; index += 2 }
                            else -> index++
                        }
                    }
                    mask(start, index)
                }
                source.startsWith("\"\"\"", index) -> {
                    index = source.indexOf("\"\"\"", index + 3).takeIf { it >= 0 }?.plus(3) ?: source.length
                    mask(start, index)
                }
                source[index] == '"' || source[index] == '\'' -> {
                    val quote = source[index++]
                    while (index < source.length) {
                        if (source[index] == '\\') index = minOf(index + 2, source.length)
                        else if (source[index++] == quote) break
                    }
                    mask(start, index)
                }
                else -> index++
            }
        }
        return String(result)
    }
}
