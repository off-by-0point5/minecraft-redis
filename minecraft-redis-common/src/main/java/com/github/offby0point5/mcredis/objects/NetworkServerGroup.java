package com.github.offby0point5.mcredis.objects;

import com.github.offby0point5.mcredis.NetRedis;
import com.github.offby0point5.mcredis.rules.JoinRules;
import com.github.offby0point5.mcredis.rules.KickRules;
import com.github.offby0point5.mcredis.rules.ServerGroupJoinRule;
import com.github.offby0point5.mcredis.rules.ServerGroupKickRule;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NetworkServerGroup {
    private static final String PREFIX = String.format("%S:server-group", NetRedis.NETWORK_PREFIX);

    private static final Map<String, NetworkServerGroup> groups = new HashMap<>();

    public static NetworkServerGroup getInstance(String groupName) {
        if (groups.containsKey(groupName)) return groups.get(groupName);
        else return new NetworkServerGroup(groupName);
    }

    private final String name;
    private final ServerGroupJoinRule joinRule;
    private final ServerGroupKickRule kickRule;
    private Set<NetworkSingleServer> members;

    private NetworkServerGroup(String groupName) {
        groups.put(groupName, this);
        name = groupName;
        try (Jedis jedis = NetRedis.getJedis()) {
            String joinRuleName = jedis.get(String.format("%s:%s:join-rule", PREFIX, name));
            joinRule = JoinRules.valueOf(joinRuleName);

            String kickRuleName = jedis.get(String.format("%s:%s:kick-rule", PREFIX, name));
            kickRule = KickRules.valueOf(kickRuleName);
        }
        update();
    }

    public void update() {
        try (Jedis jedis = NetRedis.getJedis()) {
            Set<String> memberNames = jedis.smembers(String.format("%s:%s:members", PREFIX, name));
            members = new HashSet<>();
            for (String memberName : memberNames) {
                members.add(NetworkSingleServer.getInstance(memberName));
            }
        }
    }

    public String getName() {
        return name;
    }

    public ServerGroupJoinRule getJoinRule() {
        return joinRule;
    }

    public ServerGroupKickRule getKickRule() {
        return kickRule;
    }

    public Set<NetworkSingleServer> getMembers() {
        return members;
    }
}