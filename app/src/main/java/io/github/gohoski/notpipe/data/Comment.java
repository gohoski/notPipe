package io.github.gohoski.notpipe.data;

import java.util.Date;

import io.github.gohoski.notpipe.Utils;

/**
 * Created by Gleb on 08.02.2026.
 */

public class Comment {
    public final String channel, channelThumbnail, content, channelId;
    public final Date publishedAt;

    public Comment(String channel, String channelThumbnail, String content, Date publishedAt, String channelId) {
        this.channel = Utils.stripEmoji(channel);
        this.channelThumbnail = channelThumbnail;
        this.content = Utils.stripEmoji(content);
        this.publishedAt = publishedAt;
        this.channelId = channelId;
    }
}
