package com.crayonbox.lucio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.AudioManager;

import javax.security.auth.login.LoginException;
import java.util.HashMap;
import java.util.Map;

public class LucioListener extends ListenerAdapter {

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

        private LucioListener() {
            this.musicManagers = new HashMap<>();
            this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    public static void main(String[] args) throws LoginException, RateLimitedException, InterruptedException {
        JDA jda = new JDABuilder(AccountType.BOT).setToken(args[0]).buildBlocking();
        jda.addEventListener(new LucioListener());
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if(musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Guild g = event.getGuild();
        AudioManager am = g.getAudioManager();
        if(event.getMessage().getContent().equals("!join")) {
            VoiceChannel vc = event.getMember().getVoiceState().getChannel();
            if(!am.isConnected() && !am.isAttemptingToConnect()) {
                if(vc != null) am.openAudioConnection(vc);
                else event.getTextChannel().sendMessage("You must join a voice channel first!").queue();
            }
        }
        else if(event.getMessage().getContent().equals("!leave")) {
            if(am.isConnected() && !am.isAttemptingToConnect()) am.closeAudioConnection();
            else event.getTextChannel().sendMessage("The bot is not in a channel yet!").queue();
        }
        else if(event.getMessage().getContent().equals("!list")) {
            GuildMusicManager gmm = getGuildAudioPlayer(g);
            if(!gmm.scheduler.getQueue().isEmpty()) {
                StringBuilder resp = new StringBuilder("```markdown\n# Playlist:\n");
                int i = 1;
                for(AudioTrack at : gmm.scheduler.getQueue()) {
                    resp.append("[").append(i).append("]: ").append(at.getInfo().title).append(" - ").append(at.getInfo().author).append("\n");
                }
                resp.append("```");
                event.getTextChannel().sendMessage(resp.toString()).queue();
            }
            else {
                event.getTextChannel().sendMessage("Playlist is currently empty.").queue();
            }
        }
        else {
            if(am.isConnected() && !am.isAttemptingToConnect()) {
                String[] command = event.getMessage().getContent().split(" ", 2);
                Guild guild = event.getGuild();

                if(guild != null) {
                    if("!play".equals(command[0]) && command.length == 2) {
                        loadAndPlay(event.getTextChannel(), command[1]);
                    }
                    else if("!skip".equals(command[0])) {
                        skipTrack(event.getTextChannel());
                    }
                }
            }
        }
        super.onMessageReceived(event);
    }

    private void loadAndPlay(final TextChannel channel, final String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                channel.sendMessage("Adding to queue " + track.getInfo().title).queue();

                play(musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if(firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                channel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();

                play(musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Nothing found by " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("Could not play: " + exception.getMessage()).queue();
            }
        });
    }

    private void play(GuildMusicManager musicManager, AudioTrack track) {
        musicManager.scheduler.queue(track);
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.scheduler.nextTrack();

        channel.sendMessage("Skipped to next track.").queue();
    }
}
