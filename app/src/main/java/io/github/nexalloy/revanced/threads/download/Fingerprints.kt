package io.github.nexalloy.revanced.threads.download

import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.InstructionData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

internal const val MEDIA = "com.instagram.feed.media.Media"
private const val PANDO_VIDEO_VERSION = "com.instagram.api.schemas.ImmutablePandoVideoVersion"
private const val POST_MENU_NOTE = "com.instagram.barcelona.feed.post.actionmenu.PostActionMenuSheet.<anonymous> (PostActionMenuSheet.kt:"
private const val MEDIA_MENU_NOTE = "com.instagram.barcelona.postmedia.ui.MediaActionMenuSheet.<anonymous> (MediaActionMenuSheet.kt:"
private const val ACTIVITY = "android.app.Activity"
private const val MENU_ICON_NOTE = "com.instagram.barcelona.common.ui.listcell.getMenuRowEndIcon (BdsListCell.kt:"

private const val OP_INSTANCE_OF = 0x20
private const val OP_SGET_OBJECT = 0x62
private const val OP_INVOKE_VIRTUAL = 0x6e
private const val OP_INVOKE_STATIC = 0x71

private fun InstructionData.call(): MethodData? = methodRef

// DexKit cannot decode methods that hold emoji strings. They are never the target, so treat them as empty.
private fun MethodData.decoded(): List<InstructionData> = runCatching { instructions }.getOrDefault(emptyList())

val copyLinkActionFingerprint = findClassDirect {
    findClass {
        matcher { addMethod { name = "toString"; usingEqStrings("CopyLinkAction") } }
    }.single()
}

// 456 stripped the Compose trace strings. The post menu lambda is the one that holds the post and its
// Activity and draws the CopyLinkAction row.
val postMenuRowsFingerprint = findMethodDirect {
    findMethod { matcher { name = "invoke"; usingStrings(POST_MENU_NOTE) } }.ifEmpty {
        val action = copyLinkActionFingerprint().name
        findMethod {
            matcher { name = "invoke"; declaredClass { addFieldForType(MEDIA); addFieldForType(ACTIVITY) } }
        }.filter { m -> m.decoded().any { it.opcode == OP_INSTANCE_OF && it.classRef?.name == action } }
    }.single()
}

// The media menu lambda is the one that draws the rows of the SaveImage/ShareVia/... action enum.
val mediaMenuRowsFingerprint = findMethodDirect {
    findMethod { matcher { name = "invoke"; usingStrings(MEDIA_MENU_NOTE) } }.ifEmpty {
        val actions = findClass { matcher { usingEqStrings("SaveImage", "ShareVia", "QuoteMedia") } }.single().name
        findMethod {
            matcher { name = "invoke"; addInvoke(copyLinkRow().row.descriptor) }
        }.filter { m -> m.decoded().any { it.classRef?.name == actions } }
    }.single()
}

private class CopyLinkRow(code: List<InstructionData>, action: String) {
    val row: MethodData
    val click: MethodData
    val role: MethodData
    val base: InstructionData

    init {
        val start = code.indexOfFirst { it.opcode == OP_INSTANCE_OF && it.classRef?.name == action }
        check(start >= 0)
        val rest = code.drop(start + 1)
        row = rest.first {
            it.opcode == OP_INVOKE_STATIC && it.call()?.let { m ->
                m.returnTypeName == "void" && m.paramCount == 5 && m.paramTypeNames[4] == "java.lang.String"
            } == true
        }.call()!!
        val end = rest.indexOfFirst { it.call()?.descriptor == row.descriptor }
        val body = rest.take(end)
        click = body.first {
            it.opcode == OP_INVOKE_STATIC && it.call()?.let { m ->
                m.paramCount == 5 && m.paramTypeNames[2] == "java.lang.Object" && m.returnTypeName == row.paramTypeNames[1]
            } == true
        }.call()!!
        role = body.first {
            it.opcode == OP_INVOKE_STATIC && it.call()?.let { m ->
                m.paramTypeNames == listOf("int") && m.returnTypeName == click.paramTypeNames[1]
            } == true
        }.call()!!
        base = body.first { it.opcode == OP_SGET_OBJECT && it.fieldRef != null }
    }
}

private fun DexKitBridge.copyLinkRow() =
    CopyLinkRow(postMenuRowsFingerprint().instructions, copyLinkActionFingerprint().name)

val menuRowFingerprint = findMethodDirect { copyLinkRow().row }
val menuClickModifierFingerprint = findMethodDirect { copyLinkRow().click }
val menuRoleFingerprint = findMethodDirect { copyLinkRow().role }
val menuBaseModifierFingerprint = findFieldDirect { copyLinkRow().base.fieldRef!! }

// getMenuRowEndIcon(composer, icon, defaults, tinted) sits next to the row composable in BdsListCell.kt.
val menuRowIconFingerprint = findMethodDirect {
    findMethod {
        matcher { paramTypes(null, "int", "int", "boolean"); usingStrings(MENU_ICON_NOTE) }
    }.ifEmpty {
        findMethod {
            matcher { declaredClass = copyLinkRow().row.declaredClassName; paramTypes(null, "int", "int", "boolean") }
        }
    }.single { Modifier.isStatic(it.modifiers) }
}

val mediaMenuIconFingerprint = findMethodDirect {
    val row = copyLinkRow().row
    val code = mediaMenuRowsFingerprint().instructions
    code.indices.filter { code[it].call()?.descriptor == row.descriptor }.firstNotNullOf { at ->
        (at - 1 downTo maxOf(0, at - 4)).map { code[it] }.firstOrNull {
            it.opcode == OP_INVOKE_STATIC && it.call()?.paramTypeNames == listOf(row.paramTypeNames[0], "int")
        }?.call()
    }
}

// The controller is the class whose method logs the copy_link tap, the one that is closed afterwards.
val postMenuCloseFingerprint = findMethodDirect {
    val code = findMethod { matcher { name = "invoke"; usingEqStrings("copy_link", "feed_action_sheet") } }.single().instructions
    val at = code.indexOfFirst { it.string == "copy_link" }
    val controller = code.drop(at).first { it.call() != null }.call()!!.declaredClassName
    val sheet = code.indexOfFirst { it.string == "feed_action_sheet" }
    code.drop(sheet).first {
        it.opcode == OP_INVOKE_VIRTUAL && it.call()?.let { m ->
            m.declaredClassName == controller && m.paramCount == 0 && m.returnTypeName == "void"
        } == true
    }.call()!!
}

private fun DexKitBridge.pandoGetter(owner: String, field: String, returns: String) =
    findMethod {
        matcher { declaredClass = owner; returnType = returns; paramCount = 0; usingNumbers(field.hashCode()) }
    }.single { !Modifier.isStatic(it.modifiers) }

val mediaVideoVersionsFingerprint = findMethodDirect { pandoGetter(MEDIA, "video_versions", "java.util.List") }
val mediaDashManifestFingerprint = findMethodDirect { pandoGetter(MEDIA, "video_dash_manifest", "java.lang.String") }
val mediaCarouselFingerprint = findMethodDirect { pandoGetter(MEDIA, "carousel_media", "java.util.List") }
val versionUrlFingerprint = findMethodDirect { pandoGetter(PANDO_VIDEO_VERSION, "url", "java.lang.String") }
val versionWidthFingerprint = findMethodDirect { pandoGetter(PANDO_VIDEO_VERSION, "width", "java.lang.Integer") }
val versionHeightFingerprint = findMethodDirect { pandoGetter(PANDO_VIDEO_VERSION, "height", "java.lang.Integer") }

// The media viewer top bar composable. 456 stripped its trace string and merged its lambdas, so the parameter shape is
// the anchor: (composer, modifier, 4 x Function0, 2 x Function1, Function3, 3 x int, 5 x boolean). It is unique in 456.
// The patch reads two parameters: [3] opens the viewer menu, [13] says whether the menu button is drawn.
val mediaViewerTopBarFingerprint = findMethodDirect {
    val function0 = "kotlin.jvm.functions.Function0"
    val function1 = "kotlin.jvm.functions.Function1"
    findMethod {
        matcher {
            returnType = "void"
            paramTypes(
                null, null, function0, function0, function0, function0, function1, function1, "kotlin.jvm.functions.Function3",
                "int", "int", "int", "boolean", "boolean", "boolean", "boolean", "boolean",
            )
        }
    }.single { Modifier.isStatic(it.modifiers) }
}

// MediaCache.get(id). 456 renamed it, so find the one String to Media method that reads the backing map directly.
val mediaCacheGetFingerprint = findMethodDirect {
    findMethod {
        matcher {
            declaredClass = "com.instagram.feed.media.MediaCache"
            returnType = MEDIA
            paramTypes("java.lang.String")
            addInvoke("Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;")
        }
    }.single()
}
