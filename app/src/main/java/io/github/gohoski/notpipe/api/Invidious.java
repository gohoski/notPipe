package io.github.gohoski.notpipe.api;

import android.util.Log;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONException;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.notpipe.data.Channel;
import io.github.gohoski.notpipe.data.Comment;
import io.github.gohoski.notpipe.data.Video;
import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.http.HttpClient;
import io.github.gohoski.notpipe.http.HttpRequest;
import io.github.gohoski.notpipe.Utils;

/**
 * Created by Gleb on 19.01.2026.
 * Implementation of Invidious API (https://docs.invidious.io/api/)
 */

class Invidious implements Metadata, VideoStream {
    private String baseUrl;
    private static final int VIDEO_THUMB = 4;
    private static final int AUTHOR_THUMB = 2;

    Invidious(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getName() { return "Invidious"; }
    public String getHost() {
        return baseUrl.replace("https://", "").replace("http://", "");
    }

    /**
     * Retrieves YouTube Hyped videos via playlists
     */
    @Override
    public List<VideoInfo> getPopularVideos() throws IOException {
        HttpRequest req = new HttpRequest(baseUrl, "/api/v1/playlists/" + Utils.getHypePlaylist());
        JSONArray arr = JSON.getObject(HttpClient.executeToString(req)).getArray("videos");
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            if ("video".equals(j.getString("type"))) // Sometimes there is parse-error
                videos.add(new VideoInfo(j.getString("videoId"), j.getString("title"),
                        Utils.parseUrl(baseUrl, j.getArray("videoThumbnails").getObject(VIDEO_THUMB).getString("url")),
                        j.getString("author"), "", j.getString("authorId"), Utils.formatDuration(j.getInt("lengthSeconds")),
                        -1, null));
        }
        return videos;
    }

    @Override public List<VideoInfo> search(String q) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/v1/search").addParam("q",q).addParam("type", "video").build();
        JSONArray json = JSON.getArray(HttpClient.executeToString(req));
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        for (int i = 0; i < json.size(); i++) {
            JSONObject j = json.getObject(i);
            if ("video".equals(j.getString("type"))) // sometimes the API returns a channel for some unordinary reason…
                videos.add(new VideoInfo(
                        j.getString("videoId"), j.getString("title"),
                        Utils.parseUrl(baseUrl, j.getArray("videoThumbnails").getObject(VIDEO_THUMB).getString("url")),
                        j.getString("author"),
                        Utils.parseUrl(baseUrl + "/ggpht", j.getArray("authorThumbnails").getObject(AUTHOR_THUMB).getString("url")),
                        j.getString("authorId"), Utils.formatDuration(j.getInt("lengthSeconds")),
                        j.getLong("viewCount"), new Date(j.getLong("published") * 1000L)
                ));
        } return videos;
    }

    @Override public List<String> searchSuggestions(String q) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/v1/search/suggestions").addParam("q",q).build();
        JSONArray json = JSON.getObject(HttpClient.executeToString(req)).getArray("suggestions");
        List<String> s = new ArrayList<String>();
        for (int i = 0; i < json.size(); i++) {
            s.add(json.getString(i));
        } return s;
    }

    @Override public Video getVideo(String id) throws IOException {
        HttpRequest req = new HttpRequest(baseUrl, "/api/v1/videos/"+id+"?local=true");
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        List<VideoInfo> related = new ArrayList<VideoInfo>();
        try {
            String error = json.getString("error");
            Log.e("Invidious", error);
            if (!Utils.isYouTubeBlockingError(error))
                throw new ContentUnavailableException(error);
        } catch (JSONException ignored) {}
        JSONArray arr = json.getArray("recommendedVideos");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            Date published; try {
                published = sdf.parse(j.getString("published"));
            } catch(Exception e) {
                e.printStackTrace(); published = new Date();
            }
            related.add(new VideoInfo(j.getString("videoId"), j.getString("title"),
                    Utils.parseUrl(baseUrl,j.getArray("videoThumbnails").getObject(VIDEO_THUMB).getString("url")),
                    j.getString("author"), null, j.getString("authorId"), Utils.formatDuration(j.getInt("lengthSeconds")),
                    Utils.parseTextCount(j.getString("viewCountText")), published));
        }
        return new Video(id, json.getString("title"),
                Utils.parseUrl(baseUrl, json.getArray("videoThumbnails").getObject(VIDEO_THUMB).getString("url")), json.getString("author"),
                Utils.parseUrl(baseUrl + "/ggpht", json.getArray("authorThumbnails").getObject(AUTHOR_THUMB).getString("url")),
                json.getString("authorId"), json.getInt("lengthSeconds"), json.getLong("viewCount"),
                new Date(json.getLong("published") * 1000L), json.getString("description"), json.getInt("likeCount"),
                (int)Utils.parseTextCount(json.getString("subCountText")),
                Utils.parseUrl(baseUrl, json.getArray("formatStreams").getObject(0).getString("url")), related, null);
    }

    @Override public String getVideoUrl(String id, String ignored, int ignored2) throws IOException {
        //Invidious can provide a combined stream only in 360p, so we ignore the quality variable
        //Invidious will be disabled for non-360p via the Manager
        HttpRequest req = new HttpRequest(baseUrl, "/api/v1/videos/"+id+"?local=true");
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        return Utils.parseUrl(baseUrl, json.getArray("formatStreams").getObject(0).getString("url"));
    }

    @Override public List<Comment> getComments(String id) throws IOException {
        HttpRequest req = new HttpRequest(baseUrl, "/api/v1/comments/"+id);
        JSONArray arr = JSON.getObject(HttpClient.executeToString(req)).getArray("comments");
        List<Comment> comments = new ArrayList<Comment>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            String thumbnail; try {
                thumbnail = Utils.parseUrl(baseUrl + "/ggpht", j.getString("authorThumbnail"));
            } catch (JSONException ignored) {
                try {
                    thumbnail = Utils.parseUrl(baseUrl + "/ggpht", j.getArray("authorThumbnails").getObject(0).getString("url"));
                } catch(Exception ignored2) {thumbnail = null;}
            }
            comments.add(new Comment(j.getString("author"), thumbnail,
                    j.getString("content"), new Date(j.getLong("published") * 1000L), j.getString("authorId")));
        }
        return comments;
    }

    @Override public List<VideoInfo> getRelated(String id) throws IOException {
        HttpRequest req = new HttpRequest(baseUrl, "/api/v1/videos/"+id);
        JSONObject json = JSON.getObject(HttpClient.executeToString(req, HttpClient.VIDEO_TIMEOUT));
        List<VideoInfo> related = new ArrayList<VideoInfo>();
        JSONArray arr = json.getArray("recommendedVideos");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            Date published; try {
                published = sdf.parse(j.getString("published"));
            } catch(Exception e) {
                e.printStackTrace(); published = new Date();
            }
            related.add(new VideoInfo(j.getString("videoId"), j.getString("title"),
                    Utils.parseUrl(baseUrl,j.getArray("videoThumbnails").getObject(VIDEO_THUMB).getString("url")),
                    j.getString("author"), null, j.getString("authorId"), Utils.formatDuration(j.getInt("lengthSeconds")),
                    Utils.parseTextCount(j.getString("viewCountText")), published));
        }
        return related;
    }

    @Override
    public String getThumbnail(String id) {
        return baseUrl + "/vi/" + id + "/mqdefault.jpg";
    }

    @Override
    public Channel getChannel(String id) throws IOException {
        if (!id.startsWith("UC")) {
            id = JSON.getArray(HttpClient.executeToString(
                    new HttpRequest.Builder(baseUrl, "/api/v1/search")
                        .addParam("q", id)
                        .addParam("type", "channel").build()
                    ))
                    .getObject(0).getString("authorId");
        }
        HttpRequest req = new HttpRequest(baseUrl, "/api/v1/channels/"+id);
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        try {
            if (json.getString("error").length() > 0)
                throw new ContentUnavailableException("Channel unavailable");
        } catch (JSONException ignored) {}
        List<VideoInfo> videos = parseChannelVideos(json.getArray("latestVideos"));
        JSONArray banners = json.getArray("authorBanners");
        String banner = "";
        if (!banners.isEmpty())
            banner = Utils.parseUrl(baseUrl + "/ggpht", banners.getObject(0).getString("url").replace("w2560", "w900"));
        return new Channel(id, json.getString("author"),
                Utils.parseUrl(baseUrl + "/ggpht", json.getArray("authorThumbnails").getObject(AUTHOR_THUMB).getString("url")), banner,
                json.getString("description"), json.getInt("subCount"), videos);
    }

    @Override
    public String getChannelIcon(String id) throws IOException {
        if (id.startsWith("UC")) {
            HttpRequest req = new HttpRequest(baseUrl, "/api/v1/channels/" + id);
            return Utils.parseUrl(baseUrl + "/ggpht", JSON.getObject(HttpClient.executeToString(req)).getArray("authorThumbnails").getObject(AUTHOR_THUMB).getString("url"));
        } else {
            HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/v1/search").addParam("q", id).addParam("type", "channel").build();
            return Utils.parseUrl(baseUrl + "/ggpht", JSON.getArray(HttpClient.executeToString(req)).getObject(0).getArray("authorThumbnails").getObject(AUTHOR_THUMB).getString("url"));
        }
    }

    @Override
    public List<VideoInfo> getChannelVideos(String id, int sort) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/v1/channels/"+id + "/videos")
                .addParam("sort_by", sort == 1 ? "popular" : "newest").build();
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        try {
            String error = json.getString("error");
            if (error != null && error.length() > 0 && !Utils.isYouTubeBlockingError(error))
                throw new ContentUnavailableException(error);
        } catch (JSONException ignored) {}
        List<VideoInfo> videos = parseChannelVideos(json.getArray("videos"));
        if (videos.isEmpty()) {
            if (sort == 1) // At this stage, we can't get popular channel videos
                return videos;
            // If the instance can't parse videos from the channel page, we'll use channel playlist instead
            req = new HttpRequest(baseUrl, "/api/v1/playlists/UU" + id.substring(2));
            JSONObject pjson = JSON.getObject(HttpClient.executeToString(req));
            try {
                String error = pjson.getString("error");
                if (error != null && error.length() > 0 && !Utils.isYouTubeBlockingError(error))
                    throw new ContentUnavailableException(error);
            } catch (JSONException ignored) {}
            JSONArray arr = pjson.getArray("videos");
            for (int i = 0; i < arr.size(); i++) {
                JSONObject j = arr.getObject(i);
                videos.add(new VideoInfo(j.getString("videoId"), j.getString("title"),
                        Utils.parseUrl(baseUrl, j.getArray("videoThumbnails").getObject(VIDEO_THUMB).getString("url")),
                        null, null, null, Utils.formatDuration(j.getInt("lengthSeconds")), -1, null));
            }
        }
        return videos;
    }

    private List<VideoInfo> parseChannelVideos(JSONArray arr) {
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        for (int i = 0; i < arr.size(); i++) {
            try {
                JSONObject j = arr.getObject(i);
                String type = j.getString("type");
                if (type.equals("video"))
                    videos.add(new VideoInfo(j.getString("videoId"), j.getString("title"),
                            Utils.parseUrl(baseUrl, j.getArray("videoThumbnails").getObject(VIDEO_THUMB).getString("url")),
                            null, null, null,
                            Utils.formatDuration(j.getInt("lengthSeconds")), j.getLong("viewCount"), new Date(j.getLong("published") * 1000L)
                    ));
                else if (type.equals("playlist")) { // Sometimes videos can be incorrectly returned as playlists, but still with valid info
                    String videoId = j.getString("playlistId");
                    videos.add(new VideoInfo(videoId, j.getString("title"),
                            baseUrl + "/vi/" + videoId + "/mqdefault.jpg", "", null, null, "", -1, null
                    ));
                } else Log.w("Invidious", "Unknown channel video type " + type);
            } catch(Exception e) { e.printStackTrace(); }
        }
        return videos;
    }
}
