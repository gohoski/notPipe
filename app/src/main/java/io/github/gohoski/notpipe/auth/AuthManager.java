package io.github.gohoski.notpipe.auth;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.gohoski.notpipe.api.Auth;
import io.github.gohoski.notpipe.api.Yt2009;
import io.github.gohoski.notpipe.api.YtFixer;
import io.github.gohoski.notpipe.data.Account;

/**
 * Created by Gleb on 29.08.2026.
 * Central manager for authentication state.
 * Persists a single Account via SharedPreferences and provides Auth instances.
 */
public class AuthManager {
    private static final String PREFS_NAME = "notPipe_auth";
    private static final String KEY_TYPE = "auth_type";
    private static final String KEY_INSTANCE = "auth_instance";
    private static final String KEY_TOKEN = "auth_token";

    private static AuthManager instance;

    private SharedPreferences prefs;
    private Account account;

    private AuthManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadAccount();
    }

    public static void init(Context context) {
        if (instance == null) {
            instance = new AuthManager(context.getApplicationContext());
        }
    }

    public static AuthManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AuthManager.init(Context) must be called first!");
        }
        return instance;
    }

    private void loadAccount() {
        String type = prefs.getString(KEY_TYPE, null);
        String instanceUrl = prefs.getString(KEY_INSTANCE, null);
        String token = prefs.getString(KEY_TOKEN, null);
        if (type != null && instanceUrl != null && token != null
                && type.length() > 0 && instanceUrl.length() > 0 && token.length() > 0) {
            account = new Account(type, instanceUrl, token);
        } else {
            account = null;
        }
    }

    /**
     * Returns true if a user is currently logged in.
     */
    public boolean isLoggedIn() {
        return account != null;
    }

    /**
     * Returns the currently persisted account or null if not logged in.
     */
    public Account getAccount() {
        return account;
    }

    /**
     * Returns an Auth implementation for the currently logged-in account,
     * or null if not logged in.
     */
    public Auth getAuth() {
        if (account == null) return null;
        return createAuth(account.type, account.instanceUrl);
    }

    /**
     * Factory for creating Auth instances by type.
     *
     * @param type one of Account.TYPE_YT2009 / Account.TYPE_YTFIXER
     * @param instanceUrl base URL of the instance
     * @return Auth implementation or null for unknown type
     */
    public Auth createAuth(String type, String instanceUrl) {
        if (type == null || instanceUrl == null) return null;
        String normalized = normalizeUrl(instanceUrl);
        if (Account.TYPE_YT2009.equals(type)) {
            return new Yt2009(normalized);
        } else if (Account.TYPE_YTFIXER.equals(type)) {
            return new YtFixer(normalized);
        }
        return null;
    }

    /**
     * Persists the given account and caches it.
     */
    public void saveAccount(Account newAccount) {
        if (newAccount == null) {
            clearAccount();
            return;
        }
        if (!isSameAccount(account, newAccount)) {
            try {
                SubscriptionCache.clear();
            } catch (Exception ignored) {}
        }
        account = newAccount;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_TYPE, newAccount.type);
        editor.putString(KEY_INSTANCE, newAccount.instanceUrl);
        editor.putString(KEY_TOKEN, newAccount.token);
        editor.commit();
    }

    /**
     * Clears persisted auth state (logout).
     */
    public void clearAccount() {
        account = null;
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_TYPE);
        editor.remove(KEY_INSTANCE);
        editor.remove(KEY_TOKEN);
        editor.commit();
        try {
            SubscriptionCache.clear();
        } catch (Exception ignored) {}
    }

    /**
     * Normalizes instance URL by trimming and removing trailing slash.
     */
    public static String normalizeUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Validates that a URL looks like a http(s) URL.
     */
    public static boolean isValidInstanceUrl(String url) {
        if (url == null) return false;
        String t = url.trim();
        return t.startsWith("http://") || t.startsWith("https://");
    }

    private static boolean isSameAccount(Account a, Account b) {
        if (a == null || b == null) return false;
        return equalsOrEmpty(a.type, b.type)
                && equalsOrEmpty(a.instanceUrl, b.instanceUrl)
                && equalsOrEmpty(a.token, b.token);
    }

    private static boolean equalsOrEmpty(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a.equals(b);
    }
}
