package org.jetbrains.research.commentupdater.utils

import gr.uom.java.xmi.diff.*
import org.refactoringminer.api.Refactoring
import org.refactoringminer.api.RefactoringType

object RefactoringUtils {
    /**
     * @return: Map from old method name to new method name
     */
    fun extractFullNameChanges(refactorings: List<Refactoring>): HashMap<MethodNameWithParam, MethodNameWithParam> {
        // If two refactorings of the same method happen, hashmap(list) will just pick last value of the same key
        val namesPairs = refactorings.filter {
            it.refactoringType == RefactoringType.RENAME_METHOD ||
            it.refactoringType == RefactoringType.CHANGE_PARAMETER_TYPE ||
            it.refactoringType == RefactoringType.ADD_PARAMETER ||
            it.refactoringType == RefactoringType.REMOVE_PARAMETER
        }.map {
            val (operationAfter, operationBefore) = when (it) {
                is RenameOperationRefactoring -> {
                    it.renamedOperation to it.originalOperation
                }
                is ChangeVariableTypeRefactoring -> {
                    it.operationAfter to it.operationBefore
                }
                is AddParameterRefactoring -> {
                    it.operationAfter to it.operationBefore
                }
                is RemoveParameterRefactoring -> {
                    it.operationAfter to it.operationBefore
                }
                else -> {
                    throw Exception("Unknown refactoring type")
                }
            }
            val newFullName = MethodNameWithParam(
                name = operationAfter.className + "." + operationAfter.name,
                paramTypes = operationAfter.parameterTypeList.map { type -> type.toQualifiedString() }
            )

            val oldFullName = MethodNameWithParam(
                name = operationBefore.className + "." + operationBefore.name,
                paramTypes = operationBefore.parameterTypeList.map { type -> type.toQualifiedString() }
            )

            newFullName to oldFullName
        }.toTypedArray()
        return hashMapOf(*namesPairs)
    }

    fun extractClassRenamesAndMoves(allRefactorings: List<Refactoring>): Map<String, String> {
        return allRefactorings.filter {
            it.refactoringType == RefactoringType.MOVE_CLASS ||
                    it.refactoringType == RefactoringType.MOVE_RENAME_CLASS
        }.associate { ref ->
            when (ref) {
                is MoveClassRefactoring -> {
                    ref.originalClassName to ref.movedClassName
                }
                is MoveAndRenameClassRefactoring -> {
                    ref.originalClassName to ref.renamedClassName
                }
                else -> {
                    throw Exception("Unknown class-level refactoring type.")
                }
            }
        }
    }
}
