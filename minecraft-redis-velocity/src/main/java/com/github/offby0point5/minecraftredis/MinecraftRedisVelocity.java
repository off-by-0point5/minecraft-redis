package com.github.offby0point5.minecraftredis;

import com.github.offby0point5.mcredis.Messaging;
import com.github.offby0point5.mcredis.proxy.Manager;
import com.github.offby0point5.mcredis.proxy.SendUtil;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@Plugin(
        id = "minecraft-redis-velocity",
        name = "Minecraft Redis",
        version = "1.0-SNAPSHOT",
        authors = {"offbyone"}
)
public class MinecraftRedisVelocity {
    private final Logger log;
    private final ProxyServer proxy;

    @Inject
    public MinecraftRedisVelocity(ProxyServer proxyServer, Logger logger) {
        log = logger;
        proxy = proxyServer;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Manager.setup(() -> proxy.getAllPlayers().stream().map(Player::getUniqueId).collect(Collectors.toSet()));
        log.info("Register plugin messaging channels.");
        // TODO: 25.06.21 get channel names from Network class
        proxy.getChannelRegistrar().register(MinecraftChannelIdentifier.create(
                "minecraftredis", Messaging.CHANNEL_SEND_PLAYER));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        Manager.shutdown();
    }

    @Subscribe
    public void onPlayerJoin(PlayerChooseInitialServerEvent event) {
        String serverName = Manager.getJoinServer(event.getPlayer().getUniqueId(), "lobby");
        Optional<RegisteredServer> optionalRegisteredServer = proxy.getServer(serverName);
        if (optionalRegisteredServer.isEmpty()) {
            event.setInitialServer(null);
            return;
        }
        event.setInitialServer(optionalRegisteredServer.get());
        Manager.sendPlayer(event.getPlayer().getUniqueId(), serverName);
    }

    @Subscribe
    public void onServerChangeRequest(PluginMessageEvent event) {
        ChannelMessageSink channelMessageSink = event.getTarget();
        if (!(event.getSource() instanceof ServerConnection)) return;
        if (!(channelMessageSink instanceof Player)) return;
        Player player = (Player) channelMessageSink;

        SendUtil.SendRequest request = SendUtil.decodeMessage(event.getData());

        switch (request.type) {
            case GROUP:
                String groupName = request.target;
                String groupServerName = Manager.getJoinServer(player.getUniqueId(), groupName);

                if (groupServerName == null) player.sendMessage(Component.text(
                        "This group has no servers to join or does not allow that!", NamedTextColor.RED));
                else {
                    Optional<RegisteredServer> optionalRegisteredServer = proxy.getServer(groupServerName);
                    if (optionalRegisteredServer.isEmpty()) player.sendMessage(Component.text(
                            "This group sent you to a server that does not exist!", NamedTextColor.RED));
                    else {
                        player.createConnectionRequest(optionalRegisteredServer.get()).fireAndForget();
                        Manager.sendPlayer(player.getUniqueId(), groupServerName);
                    }
                }
                break;
            case SERVER:
                String serverName = request.target;

                Optional<RegisteredServer> optionalRegisteredServer = proxy.getServer(serverName);
                if (optionalRegisteredServer.isEmpty()) player.sendMessage(Component.text(
                        "This server does not exist!", NamedTextColor.RED));
                else {
                    player.createConnectionRequest(optionalRegisteredServer.get()).fireAndForget();
                    Manager.sendPlayer(player.getUniqueId(), serverName);
                }
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    @Subscribe
    public void onPlayerKick(KickedFromServerEvent event) {
        Optional<Component> serverKickReason = event.getServerKickReason();
        if (event.kickedDuringServerConnect()) {
            event.setResult(KickedFromServerEvent.Notify.create(serverKickReason.orElse(
                    Component.text("Could not connect to the server!", NamedTextColor.RED))
            ));
            return;
        }

        RegisteredServer server = event.getServer();
        Player player = event.getPlayer();
        String groupName = Manager.getKickGroup(player.getUniqueId(), server.getServerInfo().getName());
        if (groupName == null) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(serverKickReason.orElse(
                    Component.text("The server you were on kicked you.", NamedTextColor.RED))
            ));
            Manager.disconnectPlayer(player.getUniqueId());
            return;
        }
        String serverName = Manager.getJoinServer(player.getUniqueId(), groupName);
        if (serverName == null) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(serverKickReason.orElse(
                    Component.text("The server you were on kicked you.", NamedTextColor.RED))
            ));
            Manager.disconnectPlayer(player.getUniqueId());
            return;
        }
        Optional<RegisteredServer> optionalRegisteredServer = proxy.getServer(serverName);
        if (optionalRegisteredServer.isEmpty()) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(serverKickReason.orElse(
                    Component.text("The server wanted you reconnected, but the new server does not exist.",
                            NamedTextColor.RED))
            ));
            Manager.disconnectPlayer(player.getUniqueId());
            return;
        }
        event.setResult(KickedFromServerEvent.RedirectPlayer.create(optionalRegisteredServer.get(),
                Component.text("You were connected to a new server.", NamedTextColor.GREEN)));
        Manager.sendPlayer(player.getUniqueId(), serverName);
    }
}
