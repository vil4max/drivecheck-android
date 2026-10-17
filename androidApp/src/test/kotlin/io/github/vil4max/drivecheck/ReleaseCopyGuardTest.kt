package io.github.vil4max.drivecheck

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Spec 002 T018 and the owner's decision of 2026-10-17 (spec 005): 1.0 ships no car screen, so no
// string in any locale may promise one.
class ReleaseCopyGuardTest {
    @Test
    fun release_guard_no_string_promises_a_car_screen() {
        val strings = File("src/main/res").walkTopDown().filter { it.isFile && it.parent.contains("values") && it.extension == "xml" }.toList()
        assertTrue(strings.size >= 3, "string files found: ${strings.size}")
        val promises = strings.flatMap { file ->
            file.readLines().filter { line -> CAR_SURFACES.any { line.contains(it, ignoreCase = true) } }.map { "${file.parentFile.name}/${file.name}: ${it.trim()}" }
        }
        assertEquals(emptyList(), promises)
    }

    private companion object {
        val CAR_SURFACES = listOf("Android Auto", "CarPlay", "Automotive")
    }
}
