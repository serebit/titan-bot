package com.serebit.autotitan.modules

import com.serebit.autotitan.api.Module
import com.serebit.autotitan.api.meta.Access
import com.serebit.autotitan.config
import com.serebit.autotitan.listeners.EventListener
import com.serebit.extensions.asMetricUnit
import com.serebit.extensions.asPercentageOf
import com.serebit.extensions.jda.sendEmbed
import com.serebit.extensions.toVerboseTimestamp
import com.serebit.loggerkt.Logger
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.entities.User
import oshi.SystemInfo
import java.lang.management.ManagementFactory
import kotlin.system.exitProcess


@Suppress("UNUSED", "TooManyFunctions")
class Owner : Module() {
    private val info by lazy { SystemInfo() }

    init {
        command(
            "shutdown",
            "Shuts down the bot with an exit code of 0.",
            Access.BotOwner()
        ) { evt ->
            Logger.info("Shutting down...")
            evt.channel.sendMessage("Shutting down.").complete()
            evt.jda.shutdown()
            config.serialize()
            exitProcess(0)
        }

        command(
            "reset",
            "Resets the modules of the bot, effectively restarting it.",
            Access.BotOwner()
        )
        { evt ->
            val message = evt.channel.sendMessage("Resetting...").complete()
            EventListener.resetModules()
            message.editMessage("Reset commands and listeners.").complete()
        }

        command(
            "systemInfo",
            "Gets information about the system that the bot is running on.",
            Access.BotOwner()
        )
        { evt ->
            val process = info.operatingSystem.getProcess(info.operatingSystem.processId)
            val processorModel = info.hardware.processor.name.replace("(\\(R\\)|\\(TM\\)|@ .+)".toRegex(), "")
            val processorCores = info.hardware.processor.physicalProcessorCount
            val processorFrequency = info.hardware.processor.vendorFreq
            val totalMemory = info.hardware.memory.total
            val usedMemory = info.hardware.memory.total - info.hardware.memory.available
            val processMemory = process.residentSetSize
            val processUptime = ManagementFactory.getRuntimeMXBean().uptime / millisecondsPerSecond
            evt.channel.sendEmbed {
                addField(
                    "Processor",
                    """
                    Model: `$processorModel`
                    Cores: `$processorCores`
                    Frequency: `${processorFrequency.asMetricUnit("Hz")}`
                    """.trimIndent(),
                    false
                )
                addField(
                    "Memory",
                    """
                    Total: `${totalMemory.asMetricUnit("B")}`
                    Used: `${usedMemory.asMetricUnit("B")} (${usedMemory.asPercentageOf(totalMemory)}%)`
                    Process: `${processMemory.asMetricUnit("B")} (${processMemory.asPercentageOf(totalMemory)}%)`
                    """.trimIndent(),
                    false
                )
                addField(
                    "Uptime",
                    """
                    System: `${info.hardware.processor.systemUptime.toVerboseTimestamp()}`
                    Process: `${processUptime.toVerboseTimestamp()}`
                    """.trimIndent(),
                    false
                )
            }.complete()
        }

        command(
            "setName",
            "Changes the bot's username.",
            Access.BotOwner(),
            delimitLastString = false
        ) { evt, name: String ->
            if (name.length !in 2..usernameMaxLength) {
                evt.channel.sendMessage("Usernames must be between 2 and 32 characters in length.").queue()
            } else {
                evt.jda.selfUser.manager.setName(name).queue()
                evt.channel.sendMessage("Renamed to $name.").queue()
            }
        }

        command(
            "setPrefix",
            "Changes the bot's command prefix.",
            Access.BotOwner()
        ) { evt, prefix: String ->
            if (prefix.isBlank() || prefix.contains("\\s".toRegex())) {
                evt.channel.sendMessage("Invalid prefix. Prefix must not be empty, and may not contain whitespace.")
                return@command
            }
            config.prefix = prefix
            config.serialize()
            evt.jda.presence.game = Game.playing("${prefix}help")
            evt.channel.sendMessage("Set prefix to `${config.prefix}`.").complete()
        }

        command(
            "blackListAdd",
            "Adds a user to the blacklist.",
            Access.BotOwner()
        ) { evt, user: User ->
            if (user.idLong in config.blackList) {
                evt.channel.sendMessage("${user.name} is already in the blacklist.").complete()
                return@command
            }
            config.blackList.add(user.idLong)
            evt.channel.sendMessage("Added ${user.name} to the blacklist.").complete()
            config.serialize()
        }

        command(
            "blackListRemove",
            "Removes a user from the blacklist.",
            Access.BotOwner()
        ) { evt, user: User ->
            if (user.idLong !in config.blackList) {
                evt.channel.sendMessage("${user.name} is not in the blacklist.").complete()
                return@command
            }
            config.blackList.remove(user.idLong)
            evt.channel.sendMessage("Removed ${user.name} from the blacklist.").complete()
            config.serialize()
        }

        command(
            "blackList",
            "Sends a list of blacklisted users in an embed.",
            Access.BotOwner()
        ) { evt ->
            if (config.blackList.isNotEmpty()) {
                evt.channel.sendEmbed {
                    addField("Blacklisted Users", config.blackList.joinToString("\n") {
                        evt.jda.getUserById(it).asMention
                    }, true)
                }.complete()
            } else {
                evt.channel.sendMessage("The blacklist is empty.").complete()
            }
        }

        command(
            "getInvite",
            "Sends the bot's invite link in a private message.",
            Access.BotOwner()
        ) { evt ->
            evt.author.openPrivateChannel().complete().sendMessage(
                "Invite link: ${evt.jda.asBot().getInviteUrl()}"
            ).complete()
        }

        command(
            "serverList",
            "Sends the list of servers that the bot is in.",
            Access.BotOwner()
        ) { evt ->
            evt.channel.sendEmbed {
                evt.jda.guilds.forEach {
                    addField(
                        "${it.name} (${it.id})",
                        "Owner: ${it.owner.asMention}\nMembers: ${it.members.size}\n",
                        true
                    )
                }
            }.complete()
        }

        command(
            "leaveServer",
            "Leaves the server in which the command is invoked.",
            Access.BotOwner()
        ) { evt ->
            evt.channel.sendMessage("Leaving the server.").complete()
            evt.guild.leave().complete()
        }

        command(
            "moduleList",
            "Sends a list of all the modules.",
            Access.BotOwner()
        )
        { evt ->
            evt.channel.sendEmbed {
                setTitle("Modules")
                setDescription(EventListener.allModules.joinToString("\n") {
                    it.name + if (it.isOptional) " (Optional)" else ""
                })
            }.complete()
        }

        command(
            "enableModule",
            "Enables the given optional module.",
            Access.BotOwner()
        ) { evt, moduleName: String ->
            if (EventListener.allModules.filter { it.isOptional }.none { it.name == moduleName }) return@command
            if (moduleName in config.enabledModules) {
                evt.channel.sendMessage("Module `$moduleName` is already enabled.").complete()
                return@command
            }
            config.enabledModules.add(moduleName)
            config.serialize()
            evt.channel.sendMessage("Enabled the `$moduleName` module.").complete()
        }

        command(
            "disableModule",
            "Disables the given optional module.",
            Access.BotOwner()
        )
        { evt, moduleName: String ->
            if (EventListener.allModules.filter { it.isOptional }.none { it.name == moduleName }) return@command
            if (moduleName !in config.enabledModules) {
                evt.channel.sendMessage("Module `$moduleName` is already disabled.").complete()
                return@command
            }
            config.enabledModules.remove(moduleName)
            config.serialize()
            evt.channel.sendMessage("Disabled the `$moduleName` module.").complete()
        }
    }

    companion object {
        private const val millisecondsPerSecond = 1000
        private const val usernameMaxLength = 32
    }
}
