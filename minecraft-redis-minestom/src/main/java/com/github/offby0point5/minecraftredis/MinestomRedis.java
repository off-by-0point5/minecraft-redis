package com.github.offby0point5.minecraftredis;

import com.github.offby0point5.mcredis.Server;
import com.github.offby0point5.mcredis.backend.Configuration;
import com.github.offby0point5.mcredis.backend.Manager;
import com.github.offby0point5.mcredis.datatype.ItemStack;
import net.minestom.server.MinecraftServer;
import net.minestom.server.extensions.Extension;

import java.io.File;
import java.net.InetSocketAddress;

public class MinestomRedis extends Extension {

    @Override
    public void initialize() {
        @SuppressWarnings("UnstableApiUsage")
        InetSocketAddress address = MinecraftServer.getNettyServer().getServerChannel().localAddress();
        Configuration.setup(new File("minecraft-redis.toml"),
                address::getHostName, address::getPort);
        Configuration.reload();

        Manager.setup(Configuration.getServerId(),
                new InetSocketAddress(Configuration.getServerHost(), Configuration.getServerPort()),
                Configuration.getServerMain(),
                new ItemStack.Builder(Configuration.getServerDefaultItemMaterial(),
                        Configuration.getServerDefaultItemName())
                        .lore(Configuration.getServerDefaultItemLore())
                        .amount(Configuration.getServerDefaultItemAmount())
                        .glowing(Configuration.getServerDefaultItemGlowing())
                        .build(),
                Configuration.getServerDefaultJoin(),
                Configuration.getServerDefaultKick());

        Server server = new Server(Configuration.getServerId());
        for (String group : Configuration.getServerGroups()) {
            server.addGroups(group);
        }
        MinecraftServer.LOGGER.info("minecraft-redis started.");
    }

    @Override
    public void terminate() {
        Manager.shutdown();
        MinecraftServer.LOGGER.info("minecraft-redis shut down.");
    }
}
