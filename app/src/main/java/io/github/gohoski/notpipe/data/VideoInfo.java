package io.github.gohoski.notpipe.data;

import java.util.Date;

import io.github.gohoski.notpipe.Utils;

/**
 * Created by Gleb on 09.01.2026.
 * Use -1 for int values if not available from API
 */

public class VideoInfo {
    public final String title, thumbnail, id, channel, channelThumbnail, duration, channelId;
    public final long views;
    public final Date publishedAt;

    public VideoInfo(String id, String title, String thumbnail, String channel, String channelThumbnail, String channelId, String duration, long views, Date publishedAt) {
        this.title = Utils.stripEmoji(title);
        this.thumbnail = thumbnail;
        this.id = id;
        this.channel = Utils.stripEmoji(channel);
        this.channelThumbnail = channelThumbnail;
        this.duration = duration;
        this.views = views;
        this.publishedAt = publishedAt;
        this.channelId = channelId;
    }

    @Override
    public String toString() {
        return "VideoInfo{" +
                "title='" + title + '\'' +
                ", thumbnail='" + thumbnail + '\'' +
                ", id='" + id + '\'' +
                ", channel='" + channel + '\'' +
                ", channelThumbnail='" + channelThumbnail + '\'' +
                ", duration='" + duration + '\'' +
                ", views=" + views +
                '}';
    }
}