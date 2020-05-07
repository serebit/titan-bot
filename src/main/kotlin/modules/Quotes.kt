package com.serebit.autotitan.modules

import com.serebit.autotitan.api.Module
import com.serebit.autotitan.api.annotations.Command
import com.serebit.autotitan.api.meta.Locale
import com.serebit.autotitan.data.DataManager
import com.serebit.autotitan.data.GuildResourceMap
import com.serebit.autotitan.extensions.jda.MESSAGE_EMBED_MAX_FIELDS
import com.serebit.autotitan.extensions.jda.chunkedBy
import com.serebit.autotitan.extensions.jda.mentionsUsers
import com.serebit.autotitan.extensions.jda.sendEmbed
import com.serebit.autotitan.extensions.limitLengthTo
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.*

@Suppress("UNUSED", "TooManyFunctions")
class Quotes : Module(isOptional = true) {
    private val dataManager = DataManager(this::class)
    private val quoteMap = dataManager.read("quotes.json") ?: GuildResourceMap<String, String>()
    private val random = Random()

    @Command(
        description = "Adds the given quote.",
        locale = Locale.GUILD,
        splitLastParameter = false
    )
    fun addQuote(evt: MessageReceivedEvent, quote: String) {
        if (evt.message.mentionsUsers) {
            evt.channel.sendMessage("Quotes containing mentions are not permitted.").complete()
            return
        }
        quoteMap[evt.guild].let {
            val quoteIndex = it.keys.map { it.toInt() }.max()?.plus(1) ?: 0
            it[quoteIndex.toString()] = quote
            evt.channel.sendMessage("Added ${evt.member!!.asMention}'s quote as number `$quoteIndex`.").complete()
        }
        dataManager.write("quotes.json", quoteMap)
    }

    @Command(
        description = "Deletes the quote at the given index.",
        locale = Locale.GUILD
    )
    fun deleteQuote(evt: MessageReceivedEvent, index: Int) {
        val quotes = quoteMap[evt.guild]

        when {
            quotes.isEmpty() -> evt.channel.sendMessage("This server has no quotes saved.").complete()
            index.toString() !in quotes -> {
                evt.channel.sendMessage("There is no quote with an index of `$index`.").complete()
            }
            else -> {
                quotes.remove(index.toString())
                dataManager.write("quotes.json", quoteMap)
                evt.channel.sendMessage("Removed quote `$index`.").complete()
            }
        }
    }

    @Command(
        description = "Gets a random quote, if any exist.",
        locale = Locale.GUILD
    )
    fun quote(evt: MessageReceivedEvent) {
        val quotes = quoteMap[evt.guild]

        if (quotes.isNotEmpty()) {
            val quote = quotes.filter { it.value.isNotBlank() }.let {
                it.values.toList()[random.nextInt(it.size)]
            }
            evt.channel.sendMessage(quote).complete()
        } else evt.channel.sendMessage("This server has no quotes saved.").complete()
    }

    @Command(description = "Gets the quote at the given index.", locale = Locale.GUILD)
    fun quote(evt: MessageReceivedEvent, index: Int) {
        val quotes = quoteMap[evt.guild]

        if (quotes.isNotEmpty()) {
            quotes[index.toString()]?.let { quote ->
                evt.channel.sendMessage(quote).complete()
            } ?: evt.channel.sendMessage("There is no quote with an index of `$index`.").complete()
        } else evt.channel.sendMessage("This server has no quotes saved.").complete()
    }

    @Command(description = "Gets the list of quotes that this server has saved.", locale = Locale.GUILD)
    fun quoteList(evt: MessageReceivedEvent) {
        val quotes = quoteMap[evt.guild]
        if (quotes.isNotEmpty()) {
            evt.channel.sendMessage("Sending a quote list in PMs.").complete()
            evt.author.openPrivateChannel().queue({ privateChannel ->
                quotes
                    .map { (index, quote) ->
                        index.limitLengthTo(MessageEmbed.TITLE_MAX_LENGTH) to
                                quote.limitLengthTo(MessageEmbed.VALUE_MAX_LENGTH)
                    }
                    .chunkedBy(MessageEmbed.EMBED_MAX_LENGTH_BOT, MESSAGE_EMBED_MAX_FIELDS) {
                        it.first.length + it.second.length
                    }
                    .forEach { embeds ->
                        privateChannel.sendEmbed {
                            embeds.forEach { addField(it.first, it.second, false) }
                        }.queue()
                    }
            }, {
                evt.channel.sendMessage(
                    "Failed to send the quote list. Make sure that you haven't blocked me!"
                ).complete()
            })
        } else evt.channel.sendMessage("This server has no quotes saved.").complete()
    }
}
