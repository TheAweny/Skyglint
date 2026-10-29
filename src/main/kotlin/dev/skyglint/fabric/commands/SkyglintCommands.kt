package dev.skyglint.fabric.commands

import com.mojang.brigadier.arguments.StringArgumentType
import dev.skyglint.fabric.GitHubUpdates
import dev.skyglint.fabric.SkyglintClient
import dev.skyglint.preview.PreviewScenario
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.*
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback

/** Native command tree; application actions remain on the runtime facade. */
object SkyglintCommands {
    fun register() = with(SkyglintClient) {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val previews = literal("preview")
                .then(literal("on").executes { setPreview(true); feedback("Synthetic preview ON"); 1 })
                .then(literal("off").executes { setPreview(false); feedback("Preview OFF"); 1 })
            PreviewScenario.entries.forEach { scenario ->
                previews.then(literal(scenario.id).executes { setPreviewScenario(scenario.id); 1 })
            }
            val commands = dispatcher.register(literal("skyglint").requires { it.attended() }
                .executes { openSettings(); 1 }
                .then(literal("update").executes { GitHubUpdates.open(); 1 }
                    .then(literal("check").executes { GitHubUpdates.check(); 1 })
                    .then(literal("download").executes { GitHubUpdates.download(); 1 }))
                .then(literal("diagnostics").executes { feedback("Diagnostics: logs=${config.current.loggingEnabled}, raw=${config.current.captureRawText}, chat=${config.current.captureChat}. on|off|export"); 1 }
                    .then(literal("on").executes { command {
                        setDiagnostics(true)
                        feedback("Full diagnostics ON and saved: events, HUD/menu sources and all received chat. Report: /skyglint diagnostics export")
                    } })
                    .then(literal("off").executes { command {
                        setDiagnostics(false)
                        feedback("Full diagnostics OFF")
                    } })
                    .then(literal("export").executes { exportDiagnostics(); 1 }))
                .then(literal("status").executes {
                    feedback(statusSummary()); 1
                })
                .then(literal("drops").executes { feedback("Drop mode=${config.current.rewardMode}; item rules=${config.current.rewardOverrides.size}. /skyglint drops hide|show|reset <item>; mode all|filtered"); 1 }
                    .then(literal("mode").then(literal("all").executes { command { changeConfig { it.copy(rewardMode = "all") }; feedback("Show all recognized rewards; explicit item rules retained") } })
                        .then(literal("filtered").executes { command { changeConfig { it.copy(rewardMode = "filtered") }; feedback("Frequent drop filter ON") } }))
                    .then(literal("hide").then(argument("item", StringArgumentType.greedyString()).executes {
                        command { setRewardRule(StringArgumentType.getString(it, "item"), "hide") }
                    }))
                    .then(literal("show").then(argument("item", StringArgumentType.greedyString()).executes {
                        command { setRewardRule(StringArgumentType.getString(it, "item"), "show") }
                    }))
                    .then(literal("reset").then(argument("item", StringArgumentType.greedyString()).executes {
                        command { setRewardRule(StringArgumentType.getString(it, "item"), null) }
                    })))
                .then(literal("logs").then(literal("on").executes { command { setLogging(true); feedback("Logging ON; config/skyglint/logs/skyglint-events.jsonl") } })
                    .then(literal("off").executes { command { setLogging(false); feedback("Logging OFF") } })
                    .then(literal("chat").then(literal("on").executes { command { setChatLogging(true); feedback("Full received chat capture ON; includes private and hidden messages") } })
                        .then(literal("off").executes { command { setChatLogging(false); feedback("Full chat capture OFF") } }))
                    .then(literal("raw").then(literal("on").executes { command { setRawLogging(true); feedback("Raw relevant server signals ON") } })
                        .then(literal("off").executes { command { setRawLogging(false); feedback("Raw signals OFF") } })))
                .then(previews)
                .then(literal("enable").executes { command { setEnabled(true) } })
                .then(literal("disable").executes { command { setEnabled(false) } }))
            dispatcher.register(literal("reborn").requires { it.attended() }.redirect(commands))
        }
    }
}
