package io.github.nexalloy.revanced.instagram.comment

import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodDirect
import org.luckypray.dexkit.result.ClassData
import java.lang.reflect.Modifier

// 456 holds the class in the comment state, 457 its interface (same name plus "Intf").
internal const val GIPHY = "com.instagram.api.schemas.CommentGiphyMediaInfo"
private const val FRAGMENT = "androidx.fragment.app.Fragment"

// The comment a menu is opened for: a state object whose parts hold the comment's GIF and its photo.
private fun ClassData.isSelectedComment() = fields.any { part ->
    runCatching { part.type.fields.any { it.typeName.startsWith(GIPHY) } }.getOrDefault(false)
}

val commentMenuRendererFingerprint = findMethodDirect {
    findMethod {
        matcher {
            modifiers = Modifier.PUBLIC
            returnType = "void"
            paramTypes(null, null, null, "java.util.List", "float")
            addInvoke { declaredClass(FRAGMENT); name = "requireContext" }
        }
    }.single { it.paramTypes[1].isSelectedComment() }
}

val menuActionSingletonsFingerprint = findFieldListDirect {
    val copy = findClass { matcher { addMethod { name = "toString"; usingEqStrings("CopyText") } } }.single { c ->
        c.superClass?.methods?.any { m ->
            m.isConstructor && m.paramTypeNames.size == 4 && m.paramTypeNames.drop(1) == listOf("int", "int", "boolean")
        } == true
    }
    val family = copy.superClass!!.name
    findClass { matcher { superClass(family) } }.flatMap { c ->
        c.fields.filter { Modifier.isStatic(it.modifiers) && it.typeName == c.name }
    }
}
