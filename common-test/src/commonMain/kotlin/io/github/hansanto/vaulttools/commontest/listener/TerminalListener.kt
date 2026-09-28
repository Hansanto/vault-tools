package io.github.hansanto.vaulttools.commontest.listener

import com.github.ajalt.mordant.animation.progress.MultiProgressBarAnimation
import com.github.ajalt.mordant.animation.progress.ProgressBarAnimation
import com.github.ajalt.mordant.animation.progress.ProgressTask
import com.github.ajalt.mordant.animation.progress.ProgressTaskUpdateScope
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import com.github.ajalt.mordant.widgets.progress.progressBarContextLayout
import com.github.ajalt.mordant.widgets.progress.text
import io.kotest.core.listeners.BeforeEachListener
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe

class TerminalListener : BeforeEachListener {

    lateinit var recorder: TerminalRecorder
    lateinit var terminal: Terminal
    lateinit var progress: ProgressBarAnimation

    override suspend fun beforeEach(testCase: TestCase) {
        recorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR)
        terminal = Terminal(terminalInterface = recorder, interactive = true)
        progress = MultiProgressBarAnimation(terminal)
    }

    fun newRecordingTask(initialContext: String = ""): RecordingTask {
        val task = progress.addTask(
            definition = progressBarContextLayout { text { context } },
            context = initialContext
        )
        return RecordingTask(task)
    }

    fun terminalOutput(): String {
        // This progress bar needs to be refreshed manually
        progress.refresh(refreshAll = true)
        return recorder.output().trim()
    }

    fun expectTerminalEmptyOutput() {
        val output = terminalOutput()
        output shouldBe ""
    }
}

class RecordingTask(private val delegate: ProgressTask<String>) : ProgressTask<String> by delegate {

    private val _contexts = mutableSetOf<String>()
    val contexts: List<String> get() {
        val result = _contexts.toList()
        if (result.firstOrNull() == "") {
            return result.drop(1)
        }
        return result
    }

    override fun update(block: ProgressTaskUpdateScope<String>.() -> Unit) {
        delegate.update {
            block()
            _contexts += this.context
        }
    }
}
