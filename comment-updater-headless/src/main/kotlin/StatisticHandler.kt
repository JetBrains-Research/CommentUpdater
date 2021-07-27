import java.util.concurrent.atomic.AtomicInteger

class StatisticHandler {

    // Internal project counters
    var foundExamples = AtomicInteger(0)
    var processedCommits = AtomicInteger(0)
    var processedMethods = AtomicInteger(0)
    var processedFileChanges = AtomicInteger(0)

    var numOfMethods = AtomicInteger(0)
    var numOfDocMethods = AtomicInteger(0)

    // Global launch counters
    var totalExamplesNumber = AtomicInteger(0)
    var numberOfCommits = 0


    fun reportSamples(): String {
        return "Found ${foundExamples.get()} examples," +
                " processed: commits ${processedCommits.get()} methods ${processedMethods.get()}" +
                " file changes ${processedFileChanges.get()}"
    }

    /* Should be called after processing one project */
    fun refresh() {
        totalExamplesNumber.addAndGet(foundExamples.get())
        foundExamples.set(0)
        processedCommits.set(0)
        processedMethods.set(0)
        processedFileChanges.set(0)
        numOfMethods.set(0)
        numOfDocMethods.set(0)
    }
}