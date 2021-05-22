// Auto-generated by GenerateSteppedRangesCodegenTestData. Do not edit!
// DONT_TARGET_EXACT_BACKEND: WASM
// KJS_WITH_FULL_RUNTIME
// WITH_RUNTIME
import kotlin.test.*

fun box(): String {
    val uintList = mutableListOf<UInt>()
    val uintProgression = 7u downTo 1u
    for (i in uintProgression step 3 step 2) {
        uintList += i
    }
    assertEquals(listOf(7u, 5u, 3u, 1u), uintList)

    val ulongList = mutableListOf<ULong>()
    val ulongProgression = 7uL downTo 1uL
    for (i in ulongProgression step 3L step 2L) {
        ulongList += i
    }
    assertEquals(listOf(7uL, 5uL, 3uL, 1uL), ulongList)

    return "OK"
}