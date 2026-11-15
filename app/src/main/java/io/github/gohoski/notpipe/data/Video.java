package io.github.gohoski.notpipe.data;

import java.util.Date;
import java.util.List;

import io.github.gohoski.notpipe.Utils;

/**
 * Created by Gleb on 12.01.2026.
 * Use -1 for int values if not available from API
 */

public class Video extends VideoInfo {
    public final int likes, subscribers, length;
    public final String description, videoUrl; // videoUrl should be for the default 360p quality (do not include if it requires another request)
    public final List<Comment> comments;
    public final List<VideoInfo> related;

    public Video(String id, String title, String thumbnail, String channel, String channelThumbnail, String channelId, int length, long views, Date publishedAt, String description, int likes, int subscribers, String videoUrl, List<VideoInfo> related, List<Comment> comments) {
        super(id, title, thumbnail, channel, channelThumbnail, channelId, Utils.formatDuration(length), views, publishedAt);
        this.description = Utils.stripEmoji(description);
        this.likes = likes;
        this.subscribers = subscribers;
        this.videoUrl = videoUrl;
        this.comments = comments;
        this.related = related;
        this.length = length;
    }

    public Video(String id, String title, String thumbnail, String channel, String channelThumbnail, String channelId, String duration, int length, long views, Date publishedAt, String description, int likes, int subscribers, String videoUrl, List<VideoInfo> related, List<Comment> comments) {
        super(id, title, thumbnail, channel, channelThumbnail, channelId, duration, views, publishedAt);
        this.description = Utils.stripEmoji(description);
        this.likes = likes;
        this.subscribers = subscribers;
        this.videoUrl = videoUrl;
        this.comments = comments;
        this.related = related;
        this.length = length;
    }

    @Override
    public String toString() {
        return "Video{" +
                "likes=" + likes +
                ", subscribers=" + subscribers +
                ", description='" + description + '\'' +
                ", videoUrl='" + videoUrl + '\'' +
                ", publishedAt=" + publishedAt +
                ", " + super.toString() +
                '}';
    }
}
