package com.github.offby0point5.mcredis.objects;

import com.github.offby0point5.mcredis.NetRedis;
import redis.clients.jedis.Jedis;

import java.util.*;

public class NetworkPlayerGroup {
    public static final String PREFIX = String.format("%S:player-group", NetRedis.NETWORK_PREFIX);

    private static final Map<UUID, NetworkPlayerGroup> groups = new HashMap<>();

    public static NetworkPlayerGroup getInstance(UUID groupID) {
        if (groups.containsKey(groupID)) return groups.get(groupID);
        else return new NetworkPlayerGroup(groupID);
    }

    public static NetworkPlayerGroup createAndGetInstance(NetworkSinglePlayer leader) {
        Objects.requireNonNull(leader);
        if (leader.getPlayerGroup() != null) {
            throw new IllegalStateException("Leader is already in a group!");
        }
        UUID groupID = UUID.randomUUID();
        // create some keys
        try (Jedis jedis = NetRedis.getJedis()) {
            jedis.set(String.format("%s:%s:leader", PREFIX, groupID), leader.getUuid().toString());
            // members are read as empty if key doesn't exist
        }
        return new NetworkPlayerGroup(groupID);
    }

    private final UUID uuid;
    private NetworkSinglePlayer leader; // TODO: 22.06.21 use a LeaderPlayer extending a PartiedPlayer
    private Set<NetworkSinglePlayer> members; // TODO: 22.06.21 use a PartiedPlayer extending a NetworkSinglePlayer
    // TODO: 22.06.21 add group invite and join

    private final String LEADER;
    private final String MEMBERS;

    private NetworkPlayerGroup(UUID groupID) {
        Objects.requireNonNull(groupID);
        LEADER = String.format("%s:%s:leader", PREFIX, groupID);
        MEMBERS = String.format("%s:%s:members", PREFIX, groupID);

        groups.put(groupID, this);
        uuid = groupID;
        update();
    }

    public void update() {
        try (Jedis jedis = NetRedis.getJedis()) {
            String leaderID = jedis.get(LEADER);
            leader = NetworkSinglePlayer.getInstance(UUID.fromString(leaderID));

            Set<String> memberPlayerIDs = jedis.smembers(MEMBERS);
            members = new HashSet<>();
            for (String playerID : memberPlayerIDs) {
                members.add(NetworkSinglePlayer.getInstance(UUID.fromString(playerID)));
            }
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    public NetworkSinglePlayer getLeader() {
        return leader;
    }

    public Set<NetworkSinglePlayer> getMembers() {
        return members;
    }
}
