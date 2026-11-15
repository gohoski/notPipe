package io.github.gohoski.notpipe.api;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
 * Created by Gleb on 17.08.2026.
 */
public class TypeType implements Metadata, VideoStream, ChannelApi {
    private final String baseUrl;

    public TypeType(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getName() {
        return "TypeType";
    }

    public String getHost() {
        return baseUrl.replace("https://", "").replace("http://", "");
    }

    @Override
    public List<VideoInfo> getPopularVideos() throws IOException {
        return null;
    }

    @Override
    public List<VideoInfo> search(String q) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/search").addParam("q", q).addParam("service", "0").build();
        JSONArray json = JSON.getObject(HttpClient.executeToString(req)).getArray("items");
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        for (int i = 0; i < json.size(); i++) {
            JSONObject j = json.getObject(i);
            String id = extractId(j.getString("id"));
            videos.add(new VideoInfo(
                    id,
                    j.getString("title"),
                    getThumbnail(id),
                    j.getString("uploaderName"),
                    proxyUrl(j.getString("uploaderAvatarUrl")),
                    extractId(j.getString("uploaderUrl")),
                    Utils.formatDuration(j.getInt("duration")),
                    j.getLong("viewCount"),
                    new Date(j.getLong("uploaded"))
            ));
        }
        return videos;
    }

    @Override
    public List<String> searchSuggestions(String q) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/suggestions").addParam("query", q).addParam("service", "0").build();
        JSONArray json = JSON.getArray(HttpClient.executeToString(req));
        List<String> s = new ArrayList<String>();
        for (int i = 0; i < json.size(); i++) {
            s.add(json.getString(i));
        } return s;
    }

    @Override
    public Video getVideo(String id) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/streams/youtube/sabr")
                .addParam("url", "https://www.youtube.com/watch?v=" + id).build();
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        List<VideoInfo> related = new ArrayList<VideoInfo>();
        JSONArray arr = json.getArray("relatedStreams");
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            String videoId = extractId(j.getString("id"));
            related.add(new VideoInfo(
                    videoId, j.getString("title"),
                    getThumbnail(videoId),
                    j.getString("uploaderName"),
                    proxyUrl(j.getString("uploaderAvatarUrl")),
                    extractId(j.getString("uploaderUrl")),
                    Utils.formatDuration(j.getInt("duration")),
                    j.getLong("viewCount"),
                    new Date(j.getLong("uploaded"))
            ));
        }
        return new Video(
                id, json.getString("title"),
                getThumbnail(id),
                json.getString("uploaderName"),
                proxyUrl(json.getString("uploaderAvatarUrl")),
                extractId(json.getString("uploaderUrl")),
                json.getInt("duration"),
                json.getLong("viewCount"),
                new Date(json.getLong("uploaded")),
                json.getString("description"),
                json.getInt("likeCount"),
                json.getInt("uploaderSubscriberCount"),
                null, related, null);
    }

    @Override
    public String getVideoUrl(String id, String quality, int timeout) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/streams/youtube/sabr/bootstrap")
                .addParam("url", "https://www.youtube.com/watch?v=" + id).build();
        JSONArray videoStreams = JSON.getObject(HttpClient.executeToString(req)).getArray("videoOnlyStreams");

        String selectedItag = null;
        String selectedCodec = null;
        int selectedHeight = 0;
        int selectedFps = 30;

        String qualityPrefix = quality + "p";

        if (videoStreams != null) {
            for (int i = 0; i < videoStreams.size(); i++) {
                JSONObject stream = videoStreams.getObject(i);
                String codec = stream.getString("codec", "");
                if (!codec.startsWith("avc")) {
                    continue;
                }

                String resolution = stream.getString("resolution", "");
                if (!resolution.startsWith(qualityPrefix)) {
                    continue;
                }

                int pIdx = resolution.indexOf('p');
                int fps = 30;
                if (pIdx != -1 && pIdx + 1 < resolution.length()) {
                    try {
                        fps = Integer.parseInt(resolution.substring(pIdx + 1));
                    } catch (NumberFormatException ignored) {
                        fps = 30;
                    }
                }

                int height = stream.getInt("height", 0);
                String itag = String.valueOf(stream.getInt("itag", 0));

                if (selectedItag == null) {
                    selectedItag = itag;
                    selectedCodec = codec;
                    selectedHeight = height;
                    selectedFps = fps;

                    // Prefer 30 FPS
                    if (fps <= 30) break;
                } else if (fps <= 30) {
                    // Replace previously found higher FPS stream with 30 FPS stream
                    selectedItag = itag;
                    selectedCodec = codec;
                    selectedHeight = height;
                    selectedFps = fps;
                    break;
                }
            }
        }

        if (selectedItag == null) {
            Log.w("TypeType", "selectedItag is null!! will use fallback, probably won't work");
            switch (quality) {
                case "480": selectedItag = "135"; selectedCodec = "avc1.4d401f"; break;
                case "720": selectedItag = "136"; selectedCodec = "avc1.4d401f"; break;
                case "1080": selectedItag = "137"; selectedCodec = "avc1.640028"; break;
                default: selectedItag = "134"; selectedCodec = "avc1.4d401e";
            }
            selectedFps = 30;
        }

        String body = "{\"url\":\"https://www.youtube.com/watch?v=" + id + "\","
                + "\"options\":{"
                + "\"mode\":\"video\","
                + "\"quality\":\"" + quality + "\","
                + "\"format\":\"mp4\","
                + "\"videoItag\":\"" + selectedItag + "\","
                + "\"height\":" + selectedHeight + ","
                + "\"fps\":" + selectedFps + ","
                + "\"videoCodec\":\"" + selectedCodec + "\","
                + "\"allowQualityFallback\":false,"
                + "\"sponsorBlock\":false,"
                + "\"sponsorBlockCategories\":[\"sponsor\",\"intro\"],"
                + "\"thumbnailOnly\":false,"
                + "\"subtitles\":{\"enabled\":false,\"auto\":false,\"embed\":false,\"languages\":[\"en\"],\"format\":\"srt\"}}}";

        req = new HttpRequest.Builder(baseUrl, "/api/downloader/jobs", "POST").body(body).build();
        String respStr = HttpClient.executeToString(req);
        JSONObject resp = JSON.getObject(respStr);
        String jobId = resp.getString("id");

        req = new HttpRequest.Builder(baseUrl, "/api/downloader/jobs/" + jobId + "/events")
                .addHeader("Accept", "text/event-stream").build();

        InputStream in = null;
        BufferedReader reader = null;
        try {
            in = HttpClient.execute(req, timeout);
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("data: ")) {
                    String data = line.substring(5).trim();
                    if (data.length() > 0) {
                        JSONObject eventJson = JSON.getObject(data);
                        String status = eventJson.getString("status", "");
                        if ("done".equals(status)) {
                            try {
                                return Utils.parseUrl(baseUrl, eventJson.getString("artifactUrl"));
                            } catch(JSONException e) { e.printStackTrace(); }
                        } else if ("error".equals(status) || "failed".equals(status)) {
                            throw new IOException(eventJson.getString("error", "Download job failed"));
                        }
                    }
                }
            }
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            } else if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }

        throw new IOException("Downloader stream ended without returning an artifact URL");
    }

    @Override
    public List<VideoInfo> getRelated(String id) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/streams/youtube/sabr")
                .addParam("url", "https://www.youtube.com/watch?v=" + id).build();
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        List<VideoInfo> related = new ArrayList<VideoInfo>();
        JSONArray arr = json.getArray("relatedStreams");
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            String videoId = extractId(j.getString("id"));
            related.add(new VideoInfo(
                    videoId, j.getString("title"),
                    getThumbnail(videoId),
                    j.getString("uploaderName"),
                    proxyUrl(j.getString("uploaderAvatarUrl")),
                    extractId(j.getString("uploaderUrl")),
                    Utils.formatDuration(j.getInt("duration")),
                    j.getLong("viewCount"),
                    new Date(j.getLong("uploaded"))
            ));
        }
        return related;
    }

    @Override
    public List<Comment> getComments(String id) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/comments")
                .addParam("url", "https://www.youtube.com/watch?v=" + id).build();
        JSONArray arr = JSON.getObject(HttpClient.executeToString(req)).getArray("comments");
        List<Comment> comments = new ArrayList<Comment>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            comments.add(new Comment(j.getString("author"), proxyUrl(j.getString("authorAvatarUrl")), Utils.cleanHtml(j.getString("text")),
                    new Date(j.getLong("publishedAt")), extractId(j.getString("authorUrl"))));
        }
        return comments;
    }

    @Override
    public String getThumbnail(String id) {
        return proxyUrl("https://i.ytimg.com/vi/" + id + "/mqdefault.jpg");
    }

    @Override
    public Channel getChannel(String id) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/channel")
                .addParam("url", id.startsWith("UC") ? "https://www.youtube.com/channel/"+id : "https://www.youtube.com/"+id).build();
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        JSONArray arr = json.getArray("videos");
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            String videoId = extractId(j.getString("id"));
            videos.add(new VideoInfo(
                    videoId, j.getString("title"),
                    proxyUrl(j.getString("thumbnailUrl")),
                    null, null, null,
                    Utils.formatDuration(j.getInt("duration")),
                    j.getLong("viewCount"),
                    new Date(j.getLong("uploaded"))
            ));
        }
        try {
            String extracted = extractId(arr.getObject(0).getString("uploaderUrl"));
            if (extracted != null && extracted.length() >= 24)
                id = extracted;
        } catch(Exception e) { e.printStackTrace(); }
        return new Channel(id, json.getString("name"), proxyUrl(json.getString("avatarUrl")),
                proxyUrl(json.getString("bannerUrl").replace("w2560", "w900")), json.getString("description"),
                json.getInt("subscriberCount"), videos);
    }

    @Override
    public String getChannelIcon(String id) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/channel")
                .addParam("url", id.startsWith("UC") ? "https://www.youtube.com/channel/"+id : "https://www.youtube.com/"+id).build();
        return proxyUrl(JSON.getObject(HttpClient.executeToString(req)).getString("avatarUrl"));
    }

    @Override
    public List<VideoInfo> getChannelVideos(String id, int sort) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/api/channel/page", "POST")
                .body("{\"url\":\"https://www.youtube.com/channel/" + id + "\",\"nextpage\":null,\"sort\":\""
                        + (sort == 1 ? "popular" : "latest") + "\"}").build();
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        JSONArray arr = json.getArray("videos");
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            String videoId = extractId(j.getString("id"));
            videos.add(new VideoInfo(
                    videoId, j.getString("title"),
                    proxyUrl(j.getString("thumbnailUrl")),
                    null, null, null,
                    Utils.formatDuration(j.getInt("duration")),
                    j.getLong("viewCount"),
                    new Date(j.getLong("uploaded"))
            ));
        }
        return videos;
    }

    private String extractId(String str) {
        if (str == null) return "";
        if (str.length() > 32) return str.substring(32);
        return str;
    }

    private String proxyUrl(String url) {
        if (url == null) return null;
        try {
            return baseUrl + "/api/proxy?url=" + URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return baseUrl + "/api/proxy?url=" + url;
        }
    }
}