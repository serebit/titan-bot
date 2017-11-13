package com.serebit.autotitan.modules.standard.audio

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.serebit.autotitan.api.Locale
import com.serebit.autotitan.api.annotations.CommandFunction
import com.serebit.autotitan.api.annotations.ExtensionClass
import com.serebit.autotitan.api.annotations.ListenerFunction
import com.serebit.extensions.jda.sendEmbed
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.VoiceChannel
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceMoveEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.managers.AudioManager
import org.apache.commons.validator.routines.UrlValidator
import java.time.OffsetDateTime

@ExtensionClass
class Audio {
    private val urlValidator = UrlValidator(arrayOf("http", "https"))
    private val playerManager = DefaultAudioPlayerManager()
    private val musicManagers = mutableMapOf<Long, GuildMusicManager>()

    init {
        AudioSourceManagers.registerRemoteSources(playerManager)
        AudioSourceManagers.registerLocalSource(playerManager)
    }

    @CommandFunction(
            description = "Joins the voice channel that the invoker is in.",
            locale = Locale.GUILD
    )
    fun joinVoice(evt: MessageReceivedEvent) {
        evt.run {
            if (member.voiceState.inVoiceChannel()) {
                connectToVoiceChannel(guild.audioManager, member.voiceState.channel)
                channel.sendMessage("Now connected to ${member.voiceState.channel.name}.").complete()
            } else {
                channel.sendMessage("You need to be in a voice channel for me to do that.").complete()
            }
        }
    }

    @CommandFunction(
            description = "Leaves the voice channel that the bot is in.",
            locale = Locale.GUILD
    )
    fun leaveVoice(evt: MessageReceivedEvent) {
        evt.run {
            if (guild.audioManager.isConnected) {
                guild.getMusicManager().scheduler.stop()
                guild.audioManager.closeAudioConnection()
            }
        }
    }

    @CommandFunction(
            description = "Plays a URL, or searches YouTube for the given search terms.",
            locale = Locale.GUILD,
            delimitFinalParameter = false
    )
    fun play(evt: MessageReceivedEvent, query: String) {
        evt.run {
            val voiceStatus = voiceStatus(evt, false)
            when (voiceStatus) {
                VoiceStatus.CONNECTED_DIFFERENT_CHANNEL, VoiceStatus.USER_NOT_CONNECTED -> {
                    channel.sendMessage(voiceStatus.errorMessage).complete()
                    return
                }
                VoiceStatus.SELF_NOT_CONNECTED -> connectToVoiceChannel(guild.audioManager, member.voiceState.channel)
                else -> Unit
            }
            val audioManager = guild.getMusicManager()
            val formattedQuery = if (urlValidator.isValid(query)) {
                query
            } else {
                "ytsearch:$query"
            }
            playerManager.loadItemOrdered(audioManager, formattedQuery, object : AudioLoadResultHandler {
                override fun trackLoaded(track: AudioTrack) {
                    channel.sendMessage("Adding ${track.info.title} to queue.").complete()
                    audioManager.scheduler.addToQueue(track)
                }

                override fun playlistLoaded(playlist: AudioPlaylist) {
                    if (playlist.isSearchResult) {
                        val track = playlist.tracks[0]
                        audioManager.scheduler.addToQueue(track)
                        channel.sendMessage("Adding ${track.info.title} to queue.").complete()
                    } else {
                        channel.sendMessage("Adding ${playlist.tracks.size} songs from ${playlist.name} to queue.").complete()
                        playlist.tracks.forEach { audioManager.scheduler.addToQueue(it) }
                    }
                }

                override fun noMatches() {
                    channel.sendMessage("Nothing found.").complete()
                }

                override fun loadFailed(exception: FriendlyException) {
                    channel.sendMessage("Could not queue: ${exception.message}").complete()
                }
            })
        }
    }

    @CommandFunction(
            description = "Skips the currently playing song.",
            locale = Locale.GUILD
    )
    fun skip(evt: MessageReceivedEvent) {
        evt.run {
            if (voiceStatus(evt, true) != VoiceStatus.CONNECTED_SAME_CHANNEL) return
            val audioManager = guild.getMusicManager()
            if (audioManager.scheduler.queue.isNotEmpty()) {
                audioManager.scheduler.skipTrack()
                channel.sendMessage("Skipped to next track.").complete()
            } else {
                channel.sendMessage("Cannot skip. No tracks are queued.").complete()
            }
        }
    }

    @CommandFunction(
            description = "Stops playing music and clears the queue.",
            locale = Locale.GUILD,
            permissions = arrayOf(Permission.VOICE_MUTE_OTHERS)
    )
    fun stop(evt: MessageReceivedEvent) {
        evt.run {
            guild.getMusicManager().scheduler.stop()
            channel.sendMessage("Cleared the music queue.").complete()
        }
    }

    @CommandFunction(
            description = "Pauses the currently playing song.",
            locale = Locale.GUILD
    )
    fun pause(evt: MessageReceivedEvent) {
        evt.run {
            if (voiceStatus(evt, true) != VoiceStatus.CONNECTED_SAME_CHANNEL) return
            val audioManager = guild.getMusicManager()
            if (audioManager.scheduler.pause()) {
                channel.sendMessage("Paused.").complete()
            }
        }
    }

    @CommandFunction(
            description = "Resumes the currently playing song.",
            locale = Locale.GUILD
    )
    fun resume(evt: MessageReceivedEvent) {
        evt.run {
            if (voiceStatus(evt, true) != VoiceStatus.CONNECTED_SAME_CHANNEL) return
            if (guild.getMusicManager().scheduler.resume()) {
                channel.sendMessage("Resumed.").complete()
            }
        }
    }

    @CommandFunction(
            description = "Sends an embed with the list of songs in the queue.",
            locale = Locale.GUILD
    )
    fun queue(evt: MessageReceivedEvent) {
        evt.run {
            if (guild.getMusicManager().player.playingTrack == null) {
                channel.sendMessage("No songs are queued.").complete()
                return
            }
            val audioManager = guild.getMusicManager()

            channel.sendEmbed {
                setAuthor(guild.selfMember.effectiveName, null, jda.selfUser.effectiveAvatarUrl)
                setTitle("Music Queue", null)
                setColor(guild.getMember(jda.selfUser).color)
                val playingTrack = audioManager.player.playingTrack
                val position = toHumanReadableDuration(playingTrack.position)
                val duration = toHumanReadableDuration(playingTrack.duration)
                val upNextList = audioManager.scheduler.queue.take(8).joinToString("\n") {
                    "${it.info.title} (${toHumanReadableDuration(it.duration)})"
                }
                addField(
                        "Now Playing",
                        "${playingTrack.info.title} ($position/$duration)",
                        false
                )
                if (audioManager.scheduler.queue.isNotEmpty()) addField(
                        "Up Next",
                        upNextList + if (audioManager.scheduler.queue.size > 8) {
                            "\n plus ${audioManager.scheduler.queue.drop(8).size} more..."
                        } else "",
                        false
                )
                setTimestamp(OffsetDateTime.now())
            }.complete()
        }
    }

    @CommandFunction(
            description = "Sets the volume.",
            locale = Locale.GUILD
    )
    fun setVolume(evt: MessageReceivedEvent, volume: Int): Unit = evt.run {
        if (voiceStatus(evt, true) != VoiceStatus.CONNECTED_SAME_CHANNEL) return
        val newVolume = when {
            volume > 100 -> 100
            volume < 0 -> 0
            else -> volume
        }
        guild.getMusicManager().player.volume = newVolume
        channel.sendMessage("Set volume to $newVolume%.").complete()
    }

    private fun Guild.getMusicManager(): GuildMusicManager {
        val audioManager = musicManagers.getOrElse(idLong, {
            val newManager = GuildMusicManager(playerManager)
            musicManagers.put(idLong, newManager)
            newManager
        })
        this.audioManager.sendingHandler = audioManager.sendHandler
        return audioManager
    }

    @ListenerFunction
    fun leaveVoiceAutomatically(evt: GuildVoiceLeaveEvent): Unit = evt.run {
        if (guild.audioManager.connectedChannel != channelLeft) return
        val nobodyLeft = guild.audioManager.connectedChannel.members.size == 1
        if (guild.audioManager.isConnected && nobodyLeft) {
            val audioPlayer = guild.getMusicManager()
            audioPlayer.scheduler.stop()
            guild.audioManager.closeAudioConnection()
        }
    }

    @ListenerFunction
    fun leaveVoiceAutomatically(evt: GuildVoiceMoveEvent): Unit = evt.run {
        if (guild.audioManager.connectedChannel != channelLeft) return
        val nobodyLeft = guild.audioManager.connectedChannel.members.size == 1
        if (guild.audioManager.isConnected && nobodyLeft) {
            guild.getMusicManager().scheduler.stop()
            guild.audioManager.closeAudioConnection()
        }
    }

    private fun connectToVoiceChannel(audioManager: AudioManager, voiceChannel: VoiceChannel) {
        if (!audioManager.isConnected && !audioManager.isAttemptingToConnect) {
            audioManager.openAudioConnection(voiceChannel)
        }
    }

    private fun voiceStatus(evt: MessageReceivedEvent, sendErrorMessage: Boolean): VoiceStatus {
        evt.run {
            val selfIsConnected = guild.audioManager.isConnected
            val userIsConnected = member.voiceState.inVoiceChannel()
            val sameChannel = member.voiceState.channel == guild.audioManager.connectedChannel
            val status = when {
                !userIsConnected -> VoiceStatus.USER_NOT_CONNECTED
                !selfIsConnected -> VoiceStatus.SELF_NOT_CONNECTED
                !sameChannel -> VoiceStatus.CONNECTED_DIFFERENT_CHANNEL
                else -> VoiceStatus.CONNECTED_SAME_CHANNEL
            }
            if (sendErrorMessage && status.errorMessage != null) channel.sendMessage(status.errorMessage).complete()
            return status
        }
    }
}

private enum class VoiceStatus(val errorMessage: String?) {
    SELF_NOT_CONNECTED("I need to be in a voice channel to do that."),
    USER_NOT_CONNECTED("You need to be in a voice channel for me to do that."),
    CONNECTED_DIFFERENT_CHANNEL("We need to be in the same voice channel for you to do that."),
    CONNECTED_SAME_CHANNEL(null)
}

fun toHumanReadableDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = (totalSeconds / 3600).toInt()
    val minutes = (totalSeconds % 3600 / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return when (hours) {
        0 -> String.format("%d:%02d", minutes, seconds)
        else -> String.format("%d:%02d:%02d", hours, minutes, seconds)
    }
}