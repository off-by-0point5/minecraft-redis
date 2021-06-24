package com.github.offby0point5.mcredis.objects;

import com.github.offby0point5.mcredis.NetRedis;
import com.github.offby0point5.mcredis.datatype.ItemStack;
import com.github.offby0point5.mcredis.rules.JoinRules;
import com.github.offby0point5.mcredis.rules.KickRules;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.util.Set;

public class Group {
    private static final String PREFIX = String.format("%s:server-group", NetRedis.NETWORK_PREFIX);

    private final String name;

    protected final String JOIN;
    protected final String KICK;
    protected final String MEMBERS;
    protected final String ITEM;

    public Group(String groupName) {
        JOIN = String.format("%s:%s:join-rule", PREFIX, groupName);
        KICK = String.format("%s:%s:kick-rule", PREFIX, groupName);
        MEMBERS = String.format("%s:%s:members", PREFIX, groupName);
        ITEM = String.format("%s:%s:item", PREFIX, groupName);

        name = groupName;
    }

    public String getName() {
        return name;
    }

    public JoinRules getJoinRule() {
        try (Jedis jedis = NetRedis.getJedis()) {
            String joinRuleName = jedis.get(JOIN);
            if (joinRuleName == null) return null;
            return JoinRules.valueOf(joinRuleName);
        }
    }

    public void setJoinRule(JoinRules joinRule) {
        try (Jedis jedis = NetRedis.getJedis()) {
            jedis.set(JOIN, joinRule.name());
        }
    }

    public KickRules getKickRule() {
        try (Jedis jedis = NetRedis.getJedis()) {
            String kickRuleName = jedis.get(KICK);
            if (kickRuleName == null) return null;
            return KickRules.valueOf(kickRuleName);
        }
    }

    public void setKickRule(KickRules kickRule) {
        try (Jedis jedis = NetRedis.getJedis()) {
            jedis.set(KICK, kickRule.name());
        }
    }

    public ItemStack getItem() {
        try (Jedis jedis = NetRedis.getJedis()) {
            String itemSerialized = jedis.get(ITEM);
            return ItemStack.deserialize(itemSerialized);
        }
    }

    public void setItem(ItemStack itemStack) {
        try (Jedis jedis = NetRedis.getJedis()) {
            jedis.set(ITEM, itemStack.serialize());
        }
    }

    public Set<String> getMembers() {
        try (Jedis jedis = NetRedis.getJedis()) {
            return jedis.smembers(MEMBERS);
        }
    }

    public void delete() {
        try (Jedis jedis = NetRedis.getJedis()) {
            Transaction transaction = jedis.multi();
            for (String serverName : getMembers()) {
                Server server = new Server(serverName);
                transaction.srem(server.ALL_GROUPS, name);
                if (server.getMain().equals(name)) transaction.set(server.MAIN_GROUP, "none");
            }
            transaction.del(JOIN);
            transaction.del(KICK);
            transaction.del(MEMBERS);
            transaction.exec();
        }
    }
}
