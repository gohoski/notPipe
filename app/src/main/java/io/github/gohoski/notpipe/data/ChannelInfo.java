package io.github.gohoski.notpipe.data;

import io.github.gohoski.notpipe.Utils;

/**
 * Created by Gleb on 04.09.2026.
 */

public class ChannelInfo {
    public final String id, title, thumbnail;
    public ChannelInfo(String id, String title, String thumbnail) {
        this.id = id;
        this.title = Utils.stripEmoji(title);
        this.thumbnail = thumbnail;
    }
}
