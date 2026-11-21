package io.github.nexalloy.revanced.instagram

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/**
 * Instagram keeps strings that many methods share in one switch method, `static String pool(int)`.
 * Callers pass an index instead of holding the string, and which strings live there changes
 * between builds, so a string fingerprint has to look in both places.
 */
internal fun MethodData.isStringPool(): Boolean =
    Modifier.isStatic(modifiers) && returnTypeName == "java.lang.String" && paramTypeNames == listOf("int")

/**
 * Methods that use [value], either directly or by asking the string pool for it.
 *
 * The pool is a packed switch starting at 0 whose cases are laid out last-to-first after the
 * default branch, so the string at position `k` in the code is case `count - 1 - k`.
 */
internal fun DexKitBridge.findMethodUsingString(value: String, extra: MethodMatcher.() -> Unit = {}): List<MethodData> {
    val direct = findMethod { matcher { usingEqStrings(value); extra() } }.filterNot { it.isStringPool() }
    val pool = findMethod {
        matcher { returnType = "java.lang.String"; paramTypes("int"); usingEqStrings(value) }
    }.singleOrNull { it.isStringPool() } ?: return direct
    // usingStrings rather than instructions: the pool holds emoji that the instruction decoder rejects.
    val strings = pool.usingStrings
    val at = strings.indexOf(value)
    if (at <= 0) return direct
    val index = strings.size - 1 - at
    val pooled = findMethod {
        matcher { addInvoke(pool.descriptor); usingNumbers(index); extra() }
    }.filter { m ->
        m.instructions.zipWithNext().any { (load, call) ->
            load.literal == index.toLong() && call.methodRef?.descriptor == pool.descriptor
        }
    }
    return direct + pooled
}
