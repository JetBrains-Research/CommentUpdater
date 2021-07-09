package org.jetbrains.research.refactorinsight

import com.intellij.AbstractBundle
import com.intellij.reference.SoftReference
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.util.*

object CommentUpdaterBundle {
    private const val BUNDLE = "CommentUpdaterBundle"
    private var INSTANCE: Reference<ResourceBundle?>? = null
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String?, vararg params: Any?): String {
        return AbstractBundle.message(bundle!!, key!!, *params)
    }

    private val bundle: ResourceBundle?
        get() {
            var bundle = SoftReference.dereference(INSTANCE)
            if (bundle == null) {
                bundle = ResourceBundle.getBundle(BUNDLE)
                INSTANCE = SoftReference(bundle)
            }
            return bundle
        }
}