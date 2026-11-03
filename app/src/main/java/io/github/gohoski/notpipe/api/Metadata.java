package io.github.gohoski.notpipe.api;

import java.io.IOException;
import java.util.List;

import io.github.gohoski.notpipe.data.Channel;
import io.github.gohoski.notpipe.data.Comment;
import io.github.gohoski.notpipe.data.Video;
import io.github.gohoski.notpipe.data.VideoInfo;

/**
 * Created by Gleb on 09.01.2026.
 */

public interface Metadata {
    String getName(); String getHost();

    /**
     * Popular videos can be implemented through YouTube hyped playlists via Utils.getHypePlaylist()
     */
    List<VideoInfo> getPopularVideos() throws IOException;
    List<VideoInfo> search(String q) throws IOException;
    List<String> searchSuggestions(String q) throws IOException;

    Video getVideo(String id) throws IOException;
    List<VideoInfo> getRelated(String id) throws IOException;
    List<Comment> getComments(String id) throws IOException;
    String getThumbnail(String id);

    /** This method resolves both @usernames and UC channel IDs! */
    Channel getChannel(String id) throws IOException;
    /** This method resolves both @usernames and UC channel IDs! */
    String getChannelIcon(String id) throws IOException;
    /**
     * Method resolves only UC ID
     * If the channel videos endpoint doesn't exist or is unreliable, you can fallback to using channel playlist
     * (`"UU" + id.substring(2)`)
     * If Metadata.getChannelVideos fails to retrieve videos, ChannelApi instance is used
     * @param id Channel ID
     * @param sort 0 = Latest, 1 = Popular
     */
    List<VideoInfo> getChannelVideos(String id, int sort) throws IOException;
}
