package io.github.gohoski.notpipe.api;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONException;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.notpipe.Utils;
import io.github.gohoski.notpipe.data.Channel;
import io.github.gohoski.notpipe.data.Comment;
import io.github.gohoski.notpipe.data.Video;
import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.http.HttpClient;
import io.github.gohoski.notpipe.http.HttpRequest;

/**
 * Created by Gleb on 05.06.2026.
 */
class Piped implements Metadata, VideoStream {
    private String baseUrl, proxyUrl;
    Piped(String baseUrl, String proxyUrl) {
        this.baseUrl = baseUrl;
        this.proxyUrl = proxyUrl;
    }

    public String getName() { return "Piped"; }
    public String getHost() {
        return replaceAll(replaceAll(baseUrl, "https://", ""), "http://", "");
    }

    /**
     * Retrieves YouTube Hyped videos via playlists
     */
    @Override
    public List<VideoInfo> getPopularVideos() throws IOException {
        HttpRequest req = new HttpRequest(baseUrl, "/playlists/" + Utils.getHypePlaylist());
        JSONArray arr = JSON.getObject(HttpClient.executeToString(req)).getArray("relatedStreams");
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            String videoId = j.getString("url").substring(9);
            videos.add(new VideoInfo(videoId, j.getString("title"),
                    getThumbnail(videoId), j.getString("uploaderName"),
                    "", j.getString("uploaderUrl").substring(9), Utils.formatDuration(j.getInt("duration")),
                    j.getLong("views"), new Date(j.getLong("uploaded"))));
        }
        return videos;
    }


    @Override
    public List<VideoInfo> search(String q) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/search").addParam("q", q).addParam("filter", "videos").build();
        JSONArray arr = JSON.getObject(HttpClient.executeToString(req)).getArray("items");
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            String videoId = j.getString("url").substring(9);
            videos.add(new VideoInfo(videoId, j.getString("title"),
                    getThumbnail(videoId), j.getString("uploaderName"),
                    Utils.parseUrl(proxyUrl, replaceAll(j.getString("uploaderAvatar"), "-no-rw?host=", "-no-rj?host=") + "&rewrite=false"),
                    j.getString("uploaderUrl").substring(9), Utils.formatDuration(j.getInt("duration")),
                    j.getLong("views"), new Date(j.getLong("uploaded"))));
        }
        return videos;
    }

    @Override
    public List<String> searchSuggestions(String q) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/suggestions").addParam("query", q).build();
        JSONArray arr = JSON.getArray(HttpClient.executeToString(req));
        List<String> s = new ArrayList<String>();
        for (int i = 0; i < arr.size(); i++) {
            s.add(arr.getString(i));
        } return s;
    }

    @Override
    public Video getVideo(String id) throws IOException {
        HttpRequest req = new HttpRequest(baseUrl, "/streams/" + id);
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        try {
            String error = json.getString("error"), message = json.getString("message");
            Log.e("Piped", error);
            if (error.length() > 0 && !Utils.isYouTubeBlockingError(message)) {
                throw new ContentUnavailableException(message);
            }
        } catch (JSONException ignored) {}
        List<VideoInfo> related = new ArrayList<VideoInfo>();
        JSONArray arr = json.getArray("relatedStreams");
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            String videoId = j.getString("url").substring(9);
            related.add(new VideoInfo(videoId, j.getString("title"),
                    getThumbnail(videoId), j.getString("uploaderName"),
                    Utils.parseUrl(proxyUrl, replaceAll(j.getString("uploaderAvatar"), "-no-rw?host=", "-no-rj?host=") + "&rewrite=false"),
                    j.getString("uploaderUrl").substring(9), Utils.formatDuration(j.getInt("duration")),
                    j.getLong("views"), new Date(j.getLong("uploaded"))));
        }
        JSONArray videoStreams = json.getArray("videoStreams");
        return new Video(id, json.getString("title"), getThumbnail(id),
                json.getString("uploader"), Utils.parseUrl(proxyUrl, replaceAll(json.getString("uploaderAvatar"), "-no-rw?host=", "-no-rj?host=") + "&rewrite=false"),
                json.getString("uploaderUrl").substring(9), json.getInt("duration"), json.getLong("views"),
                new Date(json.getLong("uploaded")), Utils.cleanHtml(json.getString("description")), json.getInt("likes"), json.getInt("uploaderSubscriberCount"),
                Utils.parseUrl(proxyUrl, videoStreams.getObject(videoStreams.size()-1).getString("url")), related, null);
    }

    @Override
    public String getVideoUrl(String id, String ignored, int ignored2) throws IOException {
        HttpRequest req = new HttpRequest(baseUrl, "/streams/" + id);
        JSONArray videoStreams = JSON.getObject(HttpClient.executeToString(req)).getArray("videoStreams");
        return Utils.parseUrl(proxyUrl, videoStreams.getObject(videoStreams.size()-1).getString("url"));
    }

    @Override
    public List<Comment> getComments(String id) throws IOException {
        HttpRequest req = new HttpRequest(baseUrl, "/comments/" + id);
        JSONArray arr = JSON.getObject(HttpClient.executeToString(req)).getArray("comments");
        List<Comment> comments = new ArrayList<Comment>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            comments.add(new Comment(j.getString("author"), Utils.parseUrl(proxyUrl, replaceAll(j.getString("thumbnail"), "-no-rw?host=", "-no-rj?host=") + "&rewrite=false"),
                    Utils.cleanHtml(j.getString("commentText")), Utils.parseRelativeDate(j.getString("commentedTime")), j.getString("commentorUrl").substring(9)));
        }
        return comments;
    }

    @Override
    public List<VideoInfo> getRelated(String id) throws IOException {
        HttpRequest req = new HttpRequest(baseUrl, "/streams/" + id);
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        List<VideoInfo> related = new ArrayList<VideoInfo>();
        JSONArray arr = json.getArray("relatedStreams");
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            String videoId = j.getString("url").substring(9);
            related.add(new VideoInfo(videoId, j.getString("title"),
                    getThumbnail(videoId), j.getString("uploaderName"),
                    Utils.parseUrl(proxyUrl, replaceAll(j.getString("uploaderAvatar"), "-no-rw?host=", "-no-rj?host=") + "&rewrite=false"),
                    j.getString("uploaderUrl").substring(9), Utils.formatDuration(j.getInt("duration")),
                    j.getLong("views"), new Date(j.getLong("uploaded"))));
        }
        return related;
    }

    @Override
    public String getThumbnail(String id) {
        return proxyUrl + "/vi/" + id + "/mqdefault.jpg?host=i.ytimg.com&rewrite=false";
    }

    @Override
    public Channel getChannel(String id) throws IOException {
        if (!id.startsWith("UC")) {
            id = JSON.getObject(HttpClient.executeToString(
                    new HttpRequest.Builder(baseUrl, "/search")
                            .addParam("q", id)
                            .addParam("filter", "channels").build()
            )).getArray("items")
                    .getObject(0).getString("url").substring(9);
        }
        HttpRequest req = new HttpRequest(baseUrl, "/channel/" + id);
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        try {
            if (json.getString("error").length() > 0)
                throw new ContentUnavailableException(json.getString("message"));
        } catch (JSONException ignored) {}
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        JSONArray arr = json.getArray("relatedStreams");
        String thumbnail = Utils.parseUrl(proxyUrl, replaceAll(json.getString("avatarUrl"), "-no-rw?host=", "-no-rj?host=") + "&rewrite=false");
        for (int i = 0; i < arr.size(); i++) {
            try {
                JSONObject j = arr.getObject(i);
                String videoId = j.getString("url").substring(9);
                videos.add(new VideoInfo(videoId, j.getString("title"),
                        getThumbnail(videoId), j.getString("uploaderName"),
                        thumbnail, id, Utils.formatDuration(j.getInt("duration")),
                        j.getLong("views"), new Date(j.getLong("uploaded"))));
            } catch(Exception e) { e.printStackTrace(); }
        }
        return new Channel(json.getString("id"), json.getString("name"), thumbnail,
                Utils.parseUrl(proxyUrl, replaceAll(replaceAll(json.getString("bannerUrl"), "w2560", "w900"), "-rw?host=", "-rj?host=") + "&rewrite=false"),
                json.getString("description"), json.getInt("subscriberCount"), videos);
    }

    @Override
    public String getChannelIcon(String id) throws IOException {
        String avatarUrl;
        if (id.startsWith("UC")) {
            HttpRequest req = new HttpRequest(baseUrl, "/channel/" + id);
            avatarUrl = JSON.getObject(HttpClient.executeToString(req)).getString("avatarUrl");
        } else {
            HttpRequest req = new HttpRequest.Builder(baseUrl, "/search").addParam("q", id).addParam("filter", "channels").build();
            avatarUrl = JSON.getObject(HttpClient.executeToString(req)).getArray("items").getObject(0).getString("thumbnail");
        }
        return Utils.parseUrl(proxyUrl, replaceAll(avatarUrl, "-no-rw", "-no-rj") + "&rewrite=false");
    }

    @Override
    public List<VideoInfo> getChannelVideos(String id, int sort) throws IOException {
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        HttpRequest req = new HttpRequest(baseUrl, "/playlists/" + (sort == 0 ? "UU" : "UULP") + id.substring(2));
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        try {
            String error = json.getString("error");
            String message = json.getString("message", "");
            if (error.length() > 0 && !Utils.isYouTubeBlockingError(message)) {
                throw new ContentUnavailableException(message.length() > 0 ? message : error);
            }
        } catch (JSONException ignored) {}
        JSONArray arr = json.getArray("relatedStreams");
        for (int i = 0; i < arr.size(); i++) {
            try {
                JSONObject j = arr.getObject(i);
                String videoId = j.getString("url").substring(9);
                videos.add(new VideoInfo(videoId, j.getString("title"),
                        getThumbnail(videoId), null, null, null,
                        Utils.formatDuration(j.getInt("duration")), j.getLong("views"), new Date(j.getLong("uploaded"))));
            } catch(Exception e) { e.printStackTrace(); }
        }
        return videos;
    }

    /**
     * High-performance, memory-friendly non-regex replacement engine for Java 1.5+.
     * Resolves major performance penalties encountered in legacy standard String.replace.
     */
    private static String replaceAll(String src, String target, String replacement) {
        if (src == null || target == null || replacement == null || target.length() == 0) {
            return src;
        }
        int idx = src.indexOf(target);
        if (idx == -1) {
            return src; // Match not found: prevents instantiation of unnecessary StringBuilder
        }
        StringBuilder sb = new StringBuilder(src.length());
        int len = target.length();
        int prev = 0;
        while (idx != -1) {
            sb.append(src, prev, idx); // Appends characters directly without creating substring objects
            sb.append(replacement);
            prev = idx + len;
            idx = src.indexOf(target, prev);
        }
        sb.append(src, prev, src.length());
        return sb.toString();
    }
}