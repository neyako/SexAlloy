package io.github.nexalloy.revanced.facebook

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/**
 * Facebook keeps many string literals in a few switch methods, `static String pool(int)`. A caller
 * loads an index and calls the pool instead of holding the string, so a literal either sits in the
 * method that uses it (older builds) or only in the pool (newer ones).
 */
internal fun MethodData.isStringPool(): Boolean =
    Modifier.isStatic(modifiers) && returnTypeName == "java.lang.String" && paramTypeNames == listOf("int")

/**
 * Methods that use [value], either directly or by asking the string pool for it.
 *
 * A pool is a packed switch starting at 0 and its default branch holds a string too. In build 588 the
 * cases follow in table order after that default, so the string at position `k` in the code is case
 * `k - 1`; Instagram lays them out last-to-first, so case `count - 1 - k`. Both indexes are tried and a
 * caller must load the index right before calling the pool.
 */
internal fun DexKitBridge.findMethodUsingString(value: String, extra: MethodMatcher.() -> Unit = {}): List<MethodData> {
    val direct = findMethod { matcher { usingEqStrings(value); extra() } }.filterNot { it.isStringPool() }
    val pool = findMethod {
        matcher { returnType = "java.lang.String"; paramTypes("int"); usingEqStrings(value) }
    }.singleOrNull { it.isStringPool() } ?: return direct
    // usingStrings rather than instructions: a pool can hold emoji that the instruction decoder rejects.
    val strings = pool.usingStrings
    val at = strings.indexOf(value)
    if (at < 0) return direct
    val pooled = setOf(at - 1, strings.size - 1 - at).filter { it >= 0 }.flatMap { index ->
        findMethod {
            matcher { addInvoke(pool.descriptor); usingNumbers(index); extra() }
        }.filter { m ->
            m.instructions.zipWithNext().any { (load, call) ->
                load.literal == index.toLong() && call.methodRef?.descriptor == pool.descriptor
            }
        }
    }
    return (direct + pooled).distinctBy { it.descriptor }
}
