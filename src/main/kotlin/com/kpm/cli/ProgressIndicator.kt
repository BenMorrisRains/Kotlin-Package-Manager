package com.kpm.cli

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class ProgressIndicator(private val message: String) {
    private val isRunning = AtomicBoolean(false)
    private var thread: Thread? = null
    
    private val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    private var frameIndex = 0
    
    fun start() {
        if (isRunning.get()) return
        
        isRunning.set(true)
        thread = thread(start = true) {
            while (isRunning.get()) {
                print("\r${spinnerFrames[frameIndex]} $message")
                System.out.flush()
                frameIndex = (frameIndex + 1) % spinnerFrames.size
                Thread.sleep(80)
            }
        }
    }
    
    fun stop(finalMessage: String? = null) {
        isRunning.set(false)
        thread?.join(500) // Wait up to 500ms for thread to finish
        
        // Clear the line
        print("\r${" ".repeat(message.length + 10)}\r")
        System.out.flush()
        
        // Print final message if provided
        finalMessage?.let { println(it) }
    }
    
    fun succeed(successMessage: String = message) {
        stop("✅ $successMessage")
    }
    
    fun fail(errorMessage: String = message) {
        stop("❌ $errorMessage")
    }
    
    companion object {
        inline fun <T> withProgress(message: String, block: () -> T): T {
            val indicator = ProgressIndicator(message)
            indicator.start()
            return try {
                val result = block()
                indicator.succeed()
                result
            } catch (e: Exception) {
                indicator.fail("$message - ${e.message}")
                throw e
            }
        }
    }
}

// Simple progress bar for operations with known progress
class ProgressBar(private val total: Int, private val message: String = "") {
    private var current = 0
    private val barWidth = 40
    
    fun update(current: Int) {
        this.current = current
        render()
    }
    
    fun increment() {
        current++
        render()
    }
    
    private fun render() {
        val percentage = (current.toDouble() / total * 100).toInt()
        val filled = (current.toDouble() / total * barWidth).toInt()
        val empty = barWidth - filled
        
        val bar = "█".repeat(filled) + "░".repeat(empty)
        print("\r$message [$bar] $percentage% ($current/$total)")
        
        if (current >= total) {
            println()
        }
    }
    
    fun complete() {
        current = total
        render()
    }
}
