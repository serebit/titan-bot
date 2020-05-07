package com.serebit.autotitan.modules

import com.serebit.autotitan.api.Module
import com.serebit.autotitan.api.annotations.Command
import com.serebit.autotitan.config
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.*
import kotlin.math.roundToInt

@Suppress("UNUSED", "TooManyFunctions")
class Entertainment : Module(isOptional = true) {
    private val deterministicRandom = Random()
    private val random = Random()
    private val eightBallResponses = listOf(
        "It is certain.",
        "It is decidedly so.",
        "Without a doubt.",
        "Yes, definitely.",
        "You may rely on it.",
        "As I see it, yes.",
        "Most likely.",
        "Outlook good.",
        "Yes.",
        "Signs point to yes.",
        "Don't count on it.",
        "My reply is no.",
        "My sources say no.",
        "Outlook not so good.",
        "Very doubtful."
    )

    @Command(name = "8", description = "Answers questions in 8-ball fashion.", splitLastParameter = false)
    fun eightBall(evt: MessageReceivedEvent, @Suppress("UNUSED_PARAMETER") question: String) {
        val responseIndex = random.next(eightBallResponses.size - 1)
        evt.channel.sendMessage(eightBallResponses[responseIndex]).complete()
    }

    @Command(
        description = "Rates the given thing on a scale of 0 to $defaultRatingDenominator.",
        splitLastParameter = false
    )
    fun rate(evt: MessageReceivedEvent, thingToRate: String) {
        deterministicRandom.setSeed(
            thingToRate.normalize()
                .hashCode()
                .toLong()
                .plus(config.token.hashCode())
        )
        val rating = deterministicRandom.next(defaultRatingDenominator)
        evt.channel.sendMessage("I'd give $thingToRate a `$rating/$defaultRatingDenominator`.").complete()
    }

    private fun String.normalize(): String = this
        .toLowerCase()
        .filter { it.isLetterOrDigit() }

    private fun Random.next(bound: Int) = (nextFloat() * bound).roundToInt()

    companion object {
        private const val defaultRatingDenominator = 10
    }
}
