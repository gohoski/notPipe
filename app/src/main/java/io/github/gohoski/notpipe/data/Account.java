package io.github.gohoski.notpipe.data;

/**
 * Created by Gleb on 27.08.2026.
 */

public class Account {
    public static final String TYPE_YT2009 = "yt2009";
    public static final String TYPE_YTFIXER = "ytfixer";

    public final String type;
    public final String instanceUrl;
    public final String token;

    public Account(String type, String instanceUrl, String token) {
        this.type = type;
        this.instanceUrl = instanceUrl;
        this.token = token;
    }
}