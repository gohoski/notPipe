package io.github.gohoski.notpipe.auth;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.notpipe.NotPipe;
import io.github.gohoski.notpipe.data.Account;
import io.github.gohoski.notpipe.data.ChannelInfo;

/**
 * Created by Gleb on 23.09.2026.
 * Persistent cache of subscribed channel IDs for list-only auth APIs (e.g. yt2009)
 * Direct-check APIs should bypass this cache and implement Auth.isSubscribed directly
 */
public class SubscriptionCache {
    private static final String PREFS_NAME = "notPipe_subs";
    private static final String KEY_ACCOUNT = "subs_account";
    private static final String KEY_TIME = "subs_time";
    private static final String KEY_IDS = "subs_ids";

    private static final String SEPARATOR = ";";

    private static final long TTL_MS = 40 * 60 * 1000;

    private static String cachedAccountKey;
    private static List<String> cachedIds;
    private static long cachedTime;
    private static boolean loaded = false;

    /**
     * @return a copy of the cached channel IDs, or null on cache miss, account switch, or expiry.
     */
    public static synchronized List<String> get(Account account) {
        if (account == null) return null;
        ensureLoaded();
        if (!isSameAccount(account)) return null;
        if (isExpired(System.currentTimeMillis())) return null;
        if (cachedIds == null) return null;
        return new ArrayList<String>(cachedIds);
    }

    /**
     * @return TRUE/FALSE on cache hit, null on cache miss or expiry.
     */
    public static synchronized Boolean contains(Account account, String channelId) {
        if (account == null || channelId == null || channelId.length() == 0) return null;
        List<String> ids = get(account);
        if (ids == null) return null;
        for (int i = 0; i < ids.size(); i++) {
            if (channelId.equals(ids.get(i))) return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    /**
     * @return true if a fresh cache entry exists for this account
     */
    public static synchronized boolean isFresh(Account account) {
        if (account == null) return false;
        ensureLoaded();
        if (!isSameAccount(account)) return false;
        if (cachedIds == null) return false;
        return !isExpired(System.currentTimeMillis());
    }

    /**
     * Stores a freshly fetched subscription list for this account
     */
    public static synchronized void put(Account account, List<ChannelInfo> channels) {
        if (account == null) return;
        List<String> ids = new ArrayList<String>();
        if (channels != null) {
            for (int i = 0; i < channels.size(); i++) {
                ChannelInfo info = channels.get(i);
                if (info != null && info.id != null && info.id.length() > 0 && !ids.contains(info.id)) {
                    ids.add(info.id);
                }
            }
        }
        cachedAccountKey = accountKey(account);
        cachedIds = ids;
        cachedTime = System.currentTimeMillis();
        loaded = true;
        saveLocked();
    }

    /**
     * Optimistically patches a fresh cache entry after a successful
     * manageSubscription call. Does nothing on cache miss so a stale
     * or foreign entry is never presented as fresh.
     */
    public static synchronized void update(Account account, String channelId, boolean subscribed) {
        if (account == null || channelId == null || channelId.length() == 0) return;
        ensureLoaded();
        if (!isSameAccount(account)) return;
        if (cachedIds == null || isExpired(System.currentTimeMillis())) return;
        boolean has = cachedIds.contains(channelId);
        if (subscribed && !has) {
            cachedIds.add(channelId);
            saveLocked();
        } else if (!subscribed && has) {
            cachedIds.remove(channelId);
            saveLocked();
        }
    }

    public static synchronized void clear() {
        cachedAccountKey = null;
        cachedIds = null;
        cachedTime = 0L;
        loaded = true;
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.remove(KEY_ACCOUNT);
                editor.remove(KEY_TIME);
                editor.remove(KEY_IDS);
                editor.commit();
            }
        } catch (Exception ignored) {}
    }

    private static boolean isSameAccount(Account account) {
        if (cachedAccountKey == null) return false;
        return cachedAccountKey.equals(accountKey(account));
    }

    private static boolean isExpired(long now) {
        if (cachedIds == null) return true;
        if (now < cachedTime) return true;
        return now - cachedTime > TTL_MS;
    }

    private static String accountKey(Account account) {
        return account.type + "|" + account.instanceUrl + "|" + account.token;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        cachedAccountKey = null;
        cachedIds = null;
        cachedTime = 0L;
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) return;
            String key = prefs.getString(KEY_ACCOUNT, null);
            if (key == null || key.length() == 0) return;
            cachedAccountKey = key;
            cachedTime = prefs.getLong(KEY_TIME, 0L);
            cachedIds = parseIds(prefs.getString(KEY_IDS, ""));
        } catch (Exception ignored) {
            cachedAccountKey = null;
            cachedIds = null;
            cachedTime = 0L;
        }
    }

    private static void saveLocked() {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) return;
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_ACCOUNT, cachedAccountKey);
            editor.putLong(KEY_TIME, cachedTime);
            editor.putString(KEY_IDS, joinIds(cachedIds));
            editor.commit();
        } catch (Exception ignored) {}
    }

    private static SharedPreferences getPrefs() {
        try {
            Context ctx = NotPipe.getAppContext();
            if (ctx == null) return null;
            return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<String> parseIds(String value) {
        List<String> result = new ArrayList<String>();
        if (value == null || value.length() == 0) return result;
        String[] parts = value.split(SEPARATOR);
        for (int i = 0; i < parts.length; i++) {
            String id = parts[i].trim();
            if (id.length() > 0 && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private static String joinIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(SEPARATOR);
            sb.append(ids.get(i));
        }
        return sb.toString();
    }
}
