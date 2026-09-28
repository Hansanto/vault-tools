package io.github.hansanto.vaulttools.search.parameter

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.hansanto.vaulttools.search.output.TerminalOutput
import io.github.hansanto.vaulttools.search.render.CompoundRender
import io.github.hansanto.vaulttools.search.render.JsonRender
import io.github.hansanto.vaulttools.search.render.TextRender
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class OutputOptionsTest :
    ShouldSpec({

        class TestCommand : NoOpCliktCommand() {
            val outputOptions by OutputOptions()
        }

        should("have default values") {
            val cmd = TestCommand()
            cmd.parse(emptyList())
            cmd.outputOptions.terminalText shouldBe true
            cmd.outputOptions.terminalJson shouldBe false
        }

        for (name in listOf(
            "-ott",
            "--output-terminal-text"
        )) {
            for (terminalText in listOf(
                false,
                true
            )) {
                should("terminalText convert '$terminalText' to Boolean instance with option $name") {
                    val cmd = TestCommand()
                    val args = listOf(name, terminalText.toString())
                    cmd.parse(args)
                    cmd.outputOptions.terminalText shouldBe terminalText
                }
            }
        }

        for (name in listOf(
            "-otj",
            "--output-terminal-json"
        )) {
            for (terminalJson in listOf(
                false,
                true
            )) {
                should("terminalJson convert '$terminalJson' to Boolean instance with option $name") {
                    val cmd = TestCommand()
                    val args = listOf(name, terminalJson.toString())
                    cmd.parse(args)
                    cmd.outputOptions.terminalJson shouldBe terminalJson
                }
            }
        }

        should("getOutputs return empty list when both terminalText and terminalJson are false") {
            val cmd = TestCommand()
            cmd.parse(listOf("-ott", "false", "-otj", "false"))
            val outputs = cmd.outputOptions.getOutputs(cmd)
            outputs shouldBe emptyList()
        }

        should("getOutputs return list terminal output with text render when terminalText is true only") {
            val cmd = TestCommand()
            cmd.parse(listOf("-ott", "true", "-otj", "false"))
            val outputs = cmd.outputOptions.getOutputs(cmd)
            outputs.size shouldBe 1
            val terminalOutput = outputs[0] as TerminalOutput
            val compoundRender = terminalOutput.render as CompoundRender
            compoundRender.renders shouldBe listOf(TextRender)
            compoundRender.separator shouldBe "\n\n"
        }

        should("getOutputs return list terminal output with json render when terminalJson is true only") {
            val cmd = TestCommand()
            cmd.parse(listOf("-ott", "false", "-otj", "true"))
            val outputs = cmd.outputOptions.getOutputs(cmd)
            outputs.size shouldBe 1
            val terminalOutput = outputs[0] as TerminalOutput
            val compoundRender = terminalOutput.render as CompoundRender
            compoundRender.renders shouldBe listOf(JsonRender)
            compoundRender.separator shouldBe "\n\n"
        }

        should(
            "getOutputs return list terminal output with text and json render when both terminalText and terminalJson are true"
        ) {
            val cmd = TestCommand()
            cmd.parse(listOf("-ott", "true", "-otj", "true"))
            val outputs = cmd.outputOptions.getOutputs(cmd)
            outputs.size shouldBe 1
            val terminalOutput = outputs[0] as TerminalOutput
            val compoundRender = terminalOutput.render as CompoundRender
            compoundRender.renders shouldBe listOf(TextRender, JsonRender)
        }
    })
