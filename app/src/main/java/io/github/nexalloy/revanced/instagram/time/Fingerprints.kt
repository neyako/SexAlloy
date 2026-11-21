package io.github.nexalloy.revanced.instagram.time

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.findClassDirect
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.result.ClassData
import java.lang.reflect.Modifier

private const val REEL_ITEM = "com.instagram.model.reels.ReelItem"
private const val OP_SGET_WIDE = 0x61

val storyTimeLabelFingerprint = findMethodDirect {
    findMethod {
        matcher { declaredClass(REEL_ITEM); paramTypes("android.content.Context"); returnType = "java.lang.String" }
    }.single { !Modifier.isStatic(it.modifiers) }
}

val storyPostedSecondsGetterFingerprint = findMethodDirect {
    storyTimeLabelFingerprint().instructions.mapNotNull { it.methodRef }
        .first { it.declaredClassName == REEL_ITEM && it.returnTypeName == "long" && it.paramCount == 0 }
}

val storyClockOffsetFieldFingerprint = findFieldDirect {
    storyTimeLabelFingerprint().instructions.first { it.opcode == OP_SGET_WIDE }.fieldRef!!
}

val storyHeaderBuildersFingerprint = findMethodListDirect {
    val label = storyTimeLabelFingerprint()
    findMethod {
        matcher { addInvoke(label.descriptor) }
    }.filter { m -> m.declaredClassName != REEL_ITEM }
}

val storyHeaderTimeFormattersFingerprint = findMethodListDirect {
    val label = storyTimeLabelFingerprint()
    storyHeaderBuildersFingerprint().flatMap { it.instructions.mapNotNull { i -> i.methodRef } }.filter { m ->
        m.descriptor == label.descriptor || (m.returnTypeName == "java.lang.String" &&
            ("long" in m.paramTypeNames || "double" in m.paramTypeNames) &&
            !m.declaredClassName.startsWith("java.") && !m.declaredClassName.startsWith("android.") &&
            m.declaredClassName != REEL_ITEM)
    }.distinctBy { it.descriptor }
}

private const val CONTEXT = "android.content.Context"
private const val STRING = "java.lang.String"

private fun DexKitBridge.relativeTimeFormatter(): ClassData = findMethod {
    matcher {
        returnType = STRING
        paramCount = 10
        usingEqStrings("MMMM d")
    }
}.single { m ->
    Modifier.isStatic(m.modifiers) && m.paramTypeNames.first() == "android.content.res.Resources" &&
        m.paramTypeNames[2] == m.declaredClassName && m.paramTypeNames.drop(4) == listOf("double", "double", "boolean", "boolean", "boolean", "boolean")
}.declaredClass!!

val relativeTimeFormatterMethodsFingerprint = findMethodListDirect {
    relativeTimeFormatter().methods.filter { m ->
        !Modifier.isStatic(m.modifiers) && m.returnTypeName == STRING &&
            (m.paramTypeNames == listOf(CONTEXT, "double") || m.paramTypeNames == listOf(CONTEXT, "long"))
    }
}

private fun DexKitBridge.askingFormatter(marker: String, type: StringMatchType) = findMethod {
    matcher {
        usingStrings(listOf(marker), type)
        addInvoke { declaredClass(relativeTimeFormatter().name); paramTypes(CONTEXT, null); returnType = STRING }
    }
}.single()

val feedFooterBuilderFingerprint = findMethodDirect { askingFormatter("CoalescedFooterUseCase#getUiState", StringMatchType.Equals) }

// TODO: 456 drops Compose trace strings, so this needs a structural anchor. ShowPostTime treats it as optional.
@get:RequireAppVersion(maxVersion = "455.99.99.99.99")
val commentHeaderFingerprint = findMethodDirect {
    findMethod {
        matcher { usingStrings(listOf("com.instagram.comments.mvvm.view.compose.CommentAuthorHeader ("), StringMatchType.StartsWith) }
    }.single()
}

val commentRowTimeFieldFingerprint = findFieldDirect {
    findClass { matcher { addUsingString("row_comment_textview_time_ago", StringMatchType.Equals) } }.mapNotNull { c ->
        c.fields.filter { !Modifier.isStatic(it.modifiers) && it.typeName == "java.lang.Long" }.singleOrNull()
    }.single()
}

val commentRowTimeReadersFingerprint = findMethodListDirect {
    val time = commentRowTimeFieldFingerprint()
    findMethod {
        matcher {
            addUsingField(time.descriptor)
            addInvoke { declaredClass(relativeTimeFormatter().name); paramTypes(CONTEXT, null); returnType = STRING }
        }
    }
}

private const val PURGE = "_"

// Up to 455 the render method carries a trace marker. 456 dropped it, so fall back to shape: the
// title component is the one class that builds subtitle lines, and render is its only public method.
val reelTitleRenderFingerprint = findMethodDirect {
    findMethod {
        matcher { usingStrings(listOf(PURGE + "ClipsTitleAndSubtitleComponent_render"), StringMatchType.Contains) }
    }.singleOrNull { !Modifier.isStatic(it.modifiers) } ?: run {
        val text = reelSubtitleTextClassFingerprint()
        val title = findMethod {
            matcher { addInvoke { declaredClass(text.name); name = "<init>" } }
        }.map { it.declaredClassName }.distinct().single()
        findMethod {
            matcher { declaredClass(title); paramCount = 1; modifiers = Modifier.PUBLIC }
        }.single { !Modifier.isStatic(it.modifiers) && !it.isConstructor && it.returnTypeName != "void" }
    }
}

// Matched by its constructor: the "CyclicSubtitleWithIconComponent_render" marker string is gone in 456.
val reelSubtitleTextClassFingerprint = findClassDirect {
    findMethod {
        matcher {
            name = "<init>"
            paramTypes(
                "android.view.View\$OnClickListener", "com.instagram.clips.intf.ClipsViewerConfig",
                "com.instagram.common.session.UserSession", null, "java.lang.CharSequence", null, null, "int", "boolean",
            )
        }
    }.single().declaredClass!!
}

val mediaTakenAtGetterFingerprint = findMethodDirect {
    findMethod {
        matcher {
            declaredClass("com.instagram.feed.media.Media")
            paramCount = 0
            returnType = "java.lang.Long"
            usingNumbers("taken_at".hashCode())
        }
    }.single { !Modifier.isStatic(it.modifiers) }
}
