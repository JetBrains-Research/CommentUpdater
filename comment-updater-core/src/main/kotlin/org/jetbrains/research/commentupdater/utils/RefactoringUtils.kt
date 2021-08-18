package org.jetbrains.research.commentupdater.utils

import gr.uom.java.xmi.diff.RenameOperationRefactoring
import org.refactoringminer.api.Refactoring
import org.refactoringminer.api.RefactoringType

object RefactoringUtils {
    /**
     * @return: Map from old method name to new method name
     */
    fun extractNameChanges(refactorings: List<Refactoring>): HashMap<String, String> {
        return hashMapOf(*refactorings.filter {
            it.refactoringType == RefactoringType.RENAME_METHOD
        }.map {
            val renameRefactoring = (it as RenameOperationRefactoring)
            (renameRefactoring.renamedOperation.className + "." + renameRefactoring.renamedOperation.name
                    to
                    renameRefactoring.originalOperation.className + "." + renameRefactoring.originalOperation.name)
        }.toTypedArray())
    }
}
