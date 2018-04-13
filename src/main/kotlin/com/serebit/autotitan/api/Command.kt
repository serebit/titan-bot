package com.serebit.autotitan.api

import com.serebit.autotitan.api.meta.Access
import com.serebit.autotitan.api.meta.Restrictions
import com.serebit.autotitan.config
import com.serebit.autotitan.data.Emote
import com.serebit.extensions.jda.getMemberByMention
import com.serebit.extensions.jda.getTextChannelByMention
import com.serebit.extensions.jda.getUserByMention
import com.serebit.extensions.jda.isNotBot
import com.serebit.extensions.jda.notInBlacklist
import com.serebit.extensions.toBooleanOrNull
import com.serebit.extensions.toCharOrNull
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Channel
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.MessageEmbed
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import kotlin.reflect.KClass

internal class Command(
    val name: String,
    description: String,
    val restrictions: Restrictions,
    private val splitLastParameter: Boolean,
    private val parameterTypes: List<KClass<*>>,
    private val function: (MessageReceivedEvent, List<Any>) -> Unit
) {
    val summary = "`$name ${parameterTypes.joinToString(" ") { "<${it.simpleName}>" }}`"
    val helpField = MessageEmbed.Field(summary, buildString {
        append("$description\n")
        append("Access: ${restrictions.access.description}\n")
    }, false)

    operator fun invoke(evt: MessageReceivedEvent, parameters: List<Any>) =
        function.invoke(evt, parameters)

    fun looselyMatches(rawMessageContent: String): Boolean =
        rawMessageContent.split(" ").firstOrNull() == config.prefix + name

    fun parseTokensOrNull(evt: MessageReceivedEvent): List<Any>? {
        if (evt.isInvalidCommandInvocation) return null
        val tokens = tokenizeMessage(evt.message.contentRaw)
        return when {
            tokens[0] != config.prefix + name -> null
            parameterTypes.size != tokens.size - 1 -> null
            else -> parseTokens(evt, tokens).let { parsedTokens ->
                if (parsedTokens.any { it == null }) null else parsedTokens.filterNotNull()
            }
        }
    }

    fun isInvokeableByAuthor(evt: MessageReceivedEvent) = when (restrictions.access) {
        Access.ALL -> true
        Access.BOT_OWNER -> evt.author == evt.jda.asBot().applicationInfo.complete().owner
        Access.PRIVATE_ALL -> evt.guild == null
        Access.PRIVATE_BOT_OWNER -> evt.guild == null && evt.author == evt.jda.asBot().applicationInfo.complete().owner
        Access.GUILD_ALL -> evt.guild != null
        Access.GUILD_OWNER -> evt.member == evt.member?.guild?.owner
        Access.GUILD_BOT_OWNER -> evt.guild != null && evt.author == evt.jda.asBot().applicationInfo.complete().owner
        Access.GUILD_RANK_ABOVE -> evt.guild != null && evt.member.roles[0] > evt.guild.selfMember.roles[0]
        Access.GUILD_RANK_SAME -> evt.guild != null && evt.member.roles[0] == evt.guild.selfMember.roles[0]
        Access.GUILD_RANK_BELOW -> evt.guild != null && evt.member.roles[0] < evt.guild.selfMember.roles[0]
    }

    private fun tokenizeMessage(message: String): List<String> {
        val splitParameters = message.split(" ").filter(String::isNotBlank)
        return if (splitLastParameter) {
            splitParameters
        } else {
            splitParameters.slice(0 until parameterTypes.size) +
                splitParameters.drop(parameterTypes.size).joinToString(" ")
        }.filter(String::isNotBlank)
    }

    private fun parseTokens(evt: MessageReceivedEvent, tokens: List<String>): List<Any?> =
        parameterTypes.zip(tokens.drop(1)).map { (type, string) ->
            castParameter(evt, type, string)
        }

    @Suppress("ComplexMethod")
    private fun castParameter(
        evt: MessageReceivedEvent,
        type: KClass<out Any>,
        string: String
    ): Any? = when (type) {
        String::class -> string
        Int::class -> string.toIntOrNull()
        Long::class -> string.toLongOrNull()
        Double::class -> string.toDoubleOrNull()
        Float::class -> string.toFloatOrNull()
        Short::class -> string.toShortOrNull()
        Byte::class -> string.toByteOrNull()
        Boolean::class -> string.toBooleanOrNull()
        Char::class -> string.toCharOrNull()
        User::class -> evt.jda.getUserByMention(string)
        Member::class -> evt.guild.getMemberByMention(string)
        Channel::class -> evt.guild.getTextChannelByMention(string)
        Emote::class -> Emote.from(string, evt.jda)
        else -> null
    }

    private val MessageReceivedEvent.isValidCommandInvocation: Boolean
        get() = if (author.isNotBot && author.notInBlacklist && member.hasPermissions(restrictions.permissions)) {
            isInvokeableByAuthor(this)
        } else false

    private val MessageReceivedEvent.isInvalidCommandInvocation: Boolean get() = !isValidCommandInvocation

    private fun Member?.hasPermissions(permissions: Collection<Permission>): Boolean =
        this?.hasPermission(permissions.toMutableList()) ?: false
}
