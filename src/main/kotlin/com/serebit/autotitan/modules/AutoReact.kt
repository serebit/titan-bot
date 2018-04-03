package com.serebit.autotitan.modules

import com.serebit.autotitan.api.Module
import com.serebit.autotitan.api.annotations.Command
import com.serebit.autotitan.api.annotations.Listener
import com.serebit.autotitan.api.meta.Locale
import com.serebit.autotitan.data.DataManager
import com.serebit.autotitan.data.Emote
import com.serebit.autotitan.data.GuildResourceMap
import com.serebit.extensions.jda.addReaction
import com.serebit.extensions.jda.sendEmbed
import com.serebit.extensions.limitLengthTo
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.MessageEmbed
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import java.time.Clock
import java.time.OffsetDateTime

@Suppress("UNUSED")
class AutoReact : Module(isOptional = true) {
    private val dataManager = DataManager(this::class)
    private val reactMap = dataManager.read("reacts.json") ?: GuildResourceMap<String, MutableList<EmoteData>>()

    @Command(
        description = "Adds an autoreact with the given emote for the given word.",
        locale = Locale.GUILD,
        memberPermissions = [Permission.MESSAGE_ADD_REACTION]
    )
    fun addReact(evt: MessageReceivedEvent, word: String, emote: Emote) {
        val value = reactMap[evt.guild].getOrPut(word, ::mutableListOf)
        when {
            value.size >= maxReactionsPerMessage -> {
                evt.channel.sendMessage("There are already 20 reactions for that word.").complete()
            }
            !emote.canInteract(evt.channel) -> evt.channel.sendMessage("I can't use that emote.").complete()
            value.add(EmoteData.from(emote, evt)) -> {
                dataManager.write("reacts.json", reactMap)
                evt.channel.sendMessage("Added reaction.").complete()
            }
        }
    }

    @Command(
        description = "Removes the autoreact for the given word from the list.",
        locale = Locale.GUILD,
        memberPermissions = [Permission.MESSAGE_ADD_REACTION]
    )
    fun removeReact(evt: MessageReceivedEvent, word: String, emote: Emote) {
        if (reactMap[evt.guild].getOrPut(word, ::mutableListOf).removeIf { it.emote == emote }) {
            dataManager.write("reacts.json", reactMap)
            evt.channel.sendMessage("Removed reaction.").complete()
        }
    }

    @Command(
        description = "Deletes all autoreacts from the server.",
        locale = Locale.GUILD,
        memberPermissions = [Permission.MESSAGE_ADD_REACTION, Permission.MANAGE_SERVER],
        splitLastParameter = false
    )
    fun clearReacts(evt: MessageReceivedEvent) {
        reactMap[evt.guild].clear()
        evt.channel.sendMessage("Deleted all autoreacts from this server.").complete()
    }

    @Command(description = "Gets a list of autoreacts for the server.", locale = Locale.GUILD)
    fun reactList(evt: MessageReceivedEvent) {
        evt.channel.sendEmbed {
            reactMap[evt.guild].forEach { phrase, emotes ->
                addField(
                    emotes.joinToString("") { it.emote.toString(evt.jda) },
                    phrase.limitLengthTo(MessageEmbed.VALUE_MAX_LENGTH),
                    false
                )
            }
        }.complete()
    }

    @Listener
    fun reactToMessage(evt: GuildMessageReceivedEvent) {
        if (evt.guild in reactMap && evt.author != evt.jda.selfUser) {
            reactMap[evt.guild]
                .filter { it.key in evt.message.contentRaw }
                .values
                .flatten()
                .forEach { evt.message.addReaction(it.emote).queue() }
        }
    }

    private data class EmoteData(
        val emote: Emote,
        val creationTimestamp: String,
        val authorId: Long,
        val channelId: Long
    ) {
        val creationTime: OffsetDateTime get() = OffsetDateTime.parse(creationTimestamp)

        companion object {
            fun from(emote: Emote, evt: MessageReceivedEvent): EmoteData = EmoteData(
                emote,
                OffsetDateTime.now(Clock.systemUTC()).toString(),
                evt.author.idLong,
                evt.channel.idLong
            )
        }
    }

    companion object {
        private const val maxReactionsPerMessage = 20
    }
}
