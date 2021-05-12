package org.jetbrains.research.commentupdater.processors

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import gr.uom.java.xmi.UMLModelASTReader
import gr.uom.java.xmi.diff.*
import org.refactoringminer.api.Refactoring
import org.refactoringminer.api.RefactoringType
import java.nio.file.Files.createFile
import java.nio.file.Files.createTempDirectory

object RefactoringExtractor {
    private val LOG: Logger = Logger.getInstance("#org.jetbrains.research.commentupdater.RefactoringExtractor")

    val REFACTORINGS = setOf(
        RefactoringType.RENAME_METHOD,
        RefactoringType.ADD_PARAMETER,
        RefactoringType.REMOVE_PARAMETER,
        RefactoringType.CHANGE_RETURN_TYPE,
        RefactoringType.CHANGE_PARAMETER_TYPE,
        RefactoringType.RENAME_PARAMETER
    )

    fun methodsToRefactoringTypes(refactorings: List<Refactoring>): HashMap<String, MutableList<Refactoring>> {
        val methodToRefactorings = hashMapOf<String, MutableList<Refactoring>>()
        for (r in refactorings) {
            val methodName = when (r.refactoringType) {
                RefactoringType.RENAME_METHOD -> {
                    (r as RenameOperationRefactoring).renamedOperation.className + "." +
                            r.renamedOperation.name
                }
                RefactoringType.RENAME_PARAMETER -> {
                    (r as RenameVariableRefactoring).operationAfter.className + "." +
                            r.operationAfter.name
                }
                RefactoringType.REMOVE_PARAMETER -> {
                    (r as RemoveParameterRefactoring).operationAfter.className + "." +
                            r.operationAfter.name
                }
                RefactoringType.ADD_PARAMETER -> {
                    (r as AddParameterRefactoring).operationAfter.className + "." +
                            r.operationAfter.name
                }
                RefactoringType.CHANGE_RETURN_TYPE -> {
                    (r as ChangeReturnTypeRefactoring).operationAfter.className + "." +
                            r.operationAfter.name
                }
                RefactoringType.CHANGE_PARAMETER_TYPE -> {
                    (r as ChangeVariableTypeRefactoring).operationAfter.className + "." +
                            r.operationAfter.name
                }
                else -> {
                    ""
                }
            }
            if (methodToRefactorings.containsKey(methodName)) {
                methodToRefactorings[methodName]?.add(r)
            } else {
                methodToRefactorings[methodName] = mutableListOf(r)
            }
        }
        return methodToRefactorings
    }

    fun extract(change: Change): List<Refactoring> {
        try {
            val oldContent = change.beforeRevision?.content ?: return listOf()
            val newContent = change.afterRevision?.content ?: return listOf()
            val dir1 = createTempDirectory("version1")
            val file1 = createFile(dir1.resolve("file.java"))
            file1.toFile().writeText(oldContent)

            val dir2 = createTempDirectory("version2")
            val file2 = createFile(dir2.resolve("file.java"))
            file2.toFile().writeText(newContent)

            val model1 = UMLModelASTReader(dir1.toFile()).umlModel
            val model2 = UMLModelASTReader(dir2.toFile()).umlModel
            val modelDiff = model1.diff(model2)

            return modelDiff.refactorings.filter {
                it.refactoringType in REFACTORINGS
            }

        } catch (e: VcsException) {
            LOG.error("[ACP] Failed to get a file's content from the last revision.", e.message)
        }
        return listOf()
    }
}
