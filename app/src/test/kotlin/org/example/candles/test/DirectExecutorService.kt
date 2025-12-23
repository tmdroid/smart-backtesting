package org.example.candles.test

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class DirectExecutorService : AbstractExecutorService() {
    private var shutdown = false

    override fun execute(command: Runnable) {
        if (shutdown) {
            throw IllegalStateException("Executor is shut down")
        }
        command.run()
    }

    override fun shutdown() {
        shutdown = true
    }

    override fun shutdownNow(): MutableList<Runnable> {
        shutdown = true
        return mutableListOf()
    }

    override fun isShutdown(): Boolean = shutdown

    override fun isTerminated(): Boolean = shutdown

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
}
