package io.github.gohoski.notpipe.data;

import java.util.List;

import io.github.gohoski.notpipe.Utils;

/**
 * Created by Gleb on 12.03.2026.
 */

public class Channel extends ChannelInfo {
    public final String banner, description;
    public final int subscriberCount;
    public final List<VideoInfo> videos;

    public Channel(String id, String title, String thumbnail, String banner, String description, int subscriberCount, List<VideoInfo> videos) {
        super(id, title, thumbnail);
        this.banner = banner;
        this.description = Utils.stripEmoji(description);
        this.subscriberCount = subscriberCount;
        this.videos = videos;
    }

    @Override
    public String toString() {
        return "Channel{" +
                "\n  title='" + title + '\'' +
                "\n  thumbnail='" + thumbnail + '\'' +
                "\n  banner='" + banner + '\'' +
                "\n  description='" + description + '\'' +
                "\n  subscriberCount=" + subscriberCount +
                "\n  videos=" + videos +
                "\n}";
    }
}
