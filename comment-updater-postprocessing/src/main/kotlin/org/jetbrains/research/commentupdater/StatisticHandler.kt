package org.jetbrains.research.commentupdater

import java.util.concurrent.atomic.AtomicInteger

class StatisticHandler {

    var processedProjects = AtomicInteger(0)
    var processedSamples = AtomicInteger(0)
    var inconsistenciesCounter = AtomicInteger(0)
    var consistenciesCounter = AtomicInteger(0)


    var numOfProjects = 0

    fun report(): String {
        return "Processed ${processedSamples.get()} " +
                "Found: inconsistencies ${inconsistenciesCounter.get()} and consistencies ${consistenciesCounter.get()}"
    }
}