import com.serebit.autotitan.api.extensions.chunkedBy
import com.serebit.autotitan.api.extensions.jda.MESSAGE_EMBED_MAX_FIELDS
import com.serebit.autotitan.api.extensions.jda.mentionsUsers
import com.serebit.autotitan.api.extensions.jda.sendEmbed
import com.serebit.autotitan.api.extensions.limitLengthTo
import com.serebit.autotitan.api.meta.Access
import com.serebit.autotitan.api.module
import com.serebit.autotitan.api.parameters.LongString
import com.serebit.autotitan.data.GuildResourceList
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.MessageEmbed

fun String.trimWhitespace(): String = replace("(\\s){2,}".toRegex(), "$1$1")

module("Quotes", isOptional = true, defaultAccess = Access.Guild.All()) {
    val quoteMap = dataManager.readOrDefault("quotes.json") { GuildResourceList<String?>() }

    command("addQuote", "Adds the given quote.") { quote: LongString ->
        if (message.mentionsUsers) {
            channel.sendMessage("Quotes containing mentions are not permitted.").queue()
        } else {
            quoteMap[guild].apply {
                add(quote.value)
                channel.sendMessage("Added ${member.asMention}'s quote as number `${size - 1}`.").queue()
            }
            dataManager.write("quotes.json", quoteMap)
        }
    }

    command("deleteQuote", "Deletes the quote at the given index.") { index: Int ->
        val quotes = quoteMap[guild]

        when {
            quotes.isEmpty() -> channel.sendMessage("This server has no quotes saved.").queue()
            index !in quotes.indices -> channel.sendMessage("There is no quote with an index of `$index`.").queue()
            else -> {
                quotes[index] = null
                dataManager.write("quotes.json", quoteMap)
                channel.sendMessage("Removed quote `$index`.").queue()
            }
        }
    }

    command("quote", "Gets a random quote, if any exist.") {
        if (quoteMap[guild].isNotEmpty()) {
            channel.sendMessage(quoteMap[guild].filterNotNull().random()).queue()
        } else channel.sendMessage("This server has no quotes saved.").queue()
    }

    command("quote", "Gets the quote at the given index.") { index: Int ->
        if (quoteMap[guild].isEmpty()) {
            channel.sendMessage("This server has no quotes saved.").queue()
        } else {
            quoteMap[guild][index]?.let { channel.sendMessage(it).queue() }
                ?: channel.sendMessage("There is no quote with an index of `$index`.").queue()
        }
    }

    command("quoteList", "Gets the list of quotes that this server has saved.") {
        val quotes = quoteMap[guild]
        if (quotes.isEmpty()) {
            channel.sendMessage("This server has no quotes saved.").queue()
        } else {
            channel.sendMessage("Sending a quote list in PMs.").queue()
            author.openPrivateChannel().queue({ privateChannel ->
                quotes.filterNotNull().mapIndexed { index, quote ->
                    index.toString() to quote.trimWhitespace().limitLengthTo(MessageEmbed.VALUE_MAX_LENGTH)
                }.chunkedBy(MessageEmbed.EMBED_MAX_LENGTH_BOT, MESSAGE_EMBED_MAX_FIELDS) {
                    it.first.length + it.second.length
                }.forEach { embeds ->
                    privateChannel.sendEmbed {
                        embeds.forEach { addField(it.first, it.second, false) }
                    }.queue()
                }
            }, { channel.sendMessage("Failed to send the quote list. Make sure that you haven't blocked me!").queue() })
        }
    }

    command(
        "shuffleQuotes",
        "Removes the empty quote indices for the given server.",
        access = Access.Guild.All(Permission.MANAGE_CHANNEL)
    ) {
        if (quoteMap[guild].isEmpty()) {
            channel.sendMessage("This server has no quotes to shuffle.").queue()
        } else {
            val emptyQuotes = quoteMap[guild].count { it == null }
            quoteMap[guild].removeAll { it == null }
            dataManager.write("quotes.json", quoteMap)
            channel.sendMessage("Shuffled this server's quotes. $emptyQuotes indices were removed.").queue()
        }
    }
}
