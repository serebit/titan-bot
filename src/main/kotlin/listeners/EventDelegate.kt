package com.serebit.autotitan.listeners

import com.serebit.autotitan.NAME
import com.serebit.autotitan.VERSION
import com.serebit.autotitan.api.Module
import com.serebit.autotitan.api.extensions.jda.sendEmbed
import com.serebit.autotitan.api.module
import com.serebit.autotitan.config
import com.serebit.autotitan.data.ModuleLoader
import com.serebit.logkat.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import kotlin.system.exitProcess

internal object EventDelegate : ListenerAdapter(), CoroutineScope {
    override val coroutineContext = Dispatchers.Default
    private val moduleLoader = ModuleLoader()
    private val allModules = mutableListOf<Module>()
    private val optionalModules get() = allModules.filter { it.isOptional }
    private val loadedModules get() = allModules.filter { it.isStandard || it.name in config.enabledModules }

    fun loadModulesAsync() = async {
        addSystemModules()
        allModules.addAll(moduleLoader.loadModules())
    }

    override fun onGenericEvent(evt: Event) {
        launch {
            loadedModules.forEach { it.invoke(evt) }
        }
    }

    override fun onReady(evt: ReadyEvent) = println(
        """
            $NAME v$VERSION
            Username:    ${evt.jda.selfUser.name}
            Ping:        ${evt.jda.ping}ms
            Invite link: ${evt.jda.asBot().getInviteUrl()}
        """.trimIndent()
    )

    private fun addSystemModules() {
        module("Help") {
            command("commands", "Sends an embed with a list of commands that can be used by the invoker.") {
                channel.sendEmbed {
                    loadedModules.asSequence()
                        .sortedBy { it.name }
                        .mapNotNull { it.getInvokeableCommandField(this@command) }.toList()
                        .forEach { addField(it) }
                }.queue()
            }

            command("allCommands", "Sends an embed with all commands listed.") {
                channel.sendEmbed {
                    loadedModules.sortedBy { it.name }.forEach { module ->
                        addField(module.commandListField)
                    }
                }.queue()
            }

            command("help", "Sends an embed with general information on how to use the bot.") {
                channel.sendEmbed {
                    addField(
                        "Help",
                        """
                        My prefix is `${config.prefix}`.
                        For a list of commands, enter `${config.prefix}commands`.
                        For information on a certain command, enter `${config.prefix}help <command name>`.
                        For a list containing every command, enter `${config.prefix}allcommands`.
                        """.trimIndent(),
                        false
                    )
                }.queue()
            }

            command("help", "Gets information about the requested command.") { commandName: String ->
                val matchingCommands = loadedModules.asSequence()
                    .map { it.findCommandsByName(commandName) }
                    .filter { it.isNotEmpty() }
                    .toList()
                    .flatten()
                if (matchingCommands.isNotEmpty()) {
                    channel.sendEmbed {
                        matchingCommands.forEachIndexed { index, command ->
                            if (index > 0) addBlankField(false)
                            addField(command.helpField)
                        }
                    }.queue()
                } else channel.sendMessage("Could not find any commands matching `$commandName`.").queue()
            }
        }
        module("System") {
            command("reload") {
                val message = channel.sendMessage("Reloading modules...").complete()
                allModules.clear()
                loadModulesAsync().await()
                message.editMessage("Finished reloading modules.").complete()
            }

            command("shutdown", "Shuts down the bot with an exit code of 0.") {
                Logger.info("Shutting down...")
                channel.sendMessage("Shutting down.").queue()
                jda.shutdown()
                config.serialize()
                exitProcess(0)
            }

            command("moduleList", "Sends a list of all the modules.") {
                channel.sendEmbed {
                    setTitle("Modules")
                    setDescription(EventDelegate.allModules.joinToString("\n") {
                        it.name + if (it.isOptional) " (Optional)" else ""
                    })
                }.queue()
            }

            command("enableModule", "Enables the given optional module.") { moduleName: String ->
                if (EventDelegate.optionalModules.none { it.name == moduleName }) return@command
                if (moduleName !in config.enabledModules) {
                    config.enabledModules.add(moduleName)
                    config.serialize()
                    channel.sendMessage("Enabled the `$moduleName` module.").queue()
                } else channel.sendMessage("Module `$moduleName` is already enabled.").queue()
            }

            command("disableModule", "Disables the given optional module.") { moduleName: String ->
                if (EventDelegate.optionalModules.none { it.name == moduleName }) return@command
                if (moduleName in config.enabledModules) {
                    config.enabledModules.remove(moduleName)
                    config.serialize()
                    channel.sendMessage("Disabled the `$moduleName` module.").queue()
                } else channel.sendMessage("Module `$moduleName` is already disabled.").queue()
            }
        }
    }
}
