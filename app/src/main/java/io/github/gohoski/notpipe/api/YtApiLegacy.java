package io.github.gohoski.notpipe.api;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
 * Created by Gleb on 11.01.2026.
 * Implementation of YtAPILegacy (https://github.com/ZendoMusic/yt-api-legacy)
 */

class YtApiLegacy implements Metadata, VideoStream, Conversion {
    private String baseUrl;

    YtApiLegacy(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getName() { return "YtAPILegacy"; }
    public String getHost() {
        return baseUrl.replace("https://", "").replace("http://", "");
    }

    /**
     * Retrieves popular videos through YouTube Data API v3
     */
    @Override
    public List<VideoInfo> getPopularVideos() throws IOException {
        HttpRequest req = new HttpRequest(baseUrl, "/get_top_videos.php");
        JSONArray arr = JSON.getArray(HttpClient.executeToString(req));
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject json = arr.getObject(i);
            videos.add(new VideoInfo(json.getString("video_id"), json.getString("title"),
                    Utils.parseUrl(baseUrl, json.getString("thumbnail")), json.getString("author"),
                    Utils.parseUrl(baseUrl, json.getString("channel_thumbnail")), "",
                    json.getString("duration"), -1, null));
        }
        return videos;
    }

    @Override public List<VideoInfo> search(String q) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/get_search_videos.php").addParam("query",q).build();
        JSONArray json = JSON.getArray(HttpClient.executeToString(req));
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        for (int i = 0; i < json.size(); i++) {
            JSONObject j = json.getObject(i);
            String duration; try {
                duration = j.getString("duration");
            } catch(JSONException ignored) { duration=""; }
            long views; try {
                views = Long.parseLong(j.getString("views").replaceAll("[^0-9]", ""));
            } catch(Exception ignored) { views=-1; }
            String channelId; try {
                channelId = j.getString("channel_id");
            } catch(JSONException ignored) { channelId=""; }
            Date publishedAt; try {
                publishedAt = Utils.parseRelativeDate(j.getString("published"));
            } catch(JSONException ignored) { publishedAt=null; }
            videos.add(new VideoInfo(j.getString("video_id"), j.getString("title"),
                    Utils.parseUrl(baseUrl, j.getString("thumbnail")), j.getString("author"),
                    Utils.parseUrl(baseUrl, j.getString("channel_thumbnail")), channelId,
                    duration, views, publishedAt));
        } return videos;
    }

    @Override public List<String> searchSuggestions(String q) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/get_search_suggestions.php").addParam("query",q).build();
        JSONArray json = JSON.getObject(HttpClient.executeToString(req)).getArray("suggestions");
        List<String> s = new ArrayList<String>();
        for (int i = 0; i < json.size(); i++) {
            s.add(json.getArray(i).getString(0));
        } return s;
    }

    @Override
    public Video getVideo(String id) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/get-ytvideo-info.php").addParam("video_id", id).build();
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        String title = json.getString("title"), channel = json.getString("author");
        if (title.length() == 0 && channel.length() == 0)
            throw new ContentUnavailableException("Video unavailable");
        String dateString = json.getString("published_at");
        Date publishedAt; try {
            publishedAt = new SimpleDateFormat("MMM d, yyyy", Locale.US).parse(dateString);
        } catch(ParseException ignored) {
            try {
                publishedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(dateString.replaceAll("([+-]\\d{2}):(\\d{2})$", "$1$2"));
            } catch(ParseException ignored2) {
                try {
                    publishedAt = new SimpleDateFormat("yyyy-MM-dd").parse(dateString);
                } catch (ParseException e) {
                    e.printStackTrace();
                    publishedAt = Utils.parseRelativeDate(dateString);
                }
            }
        }

        List<Comment> comments = new ArrayList<Comment>();
        JSONArray arr = json.getArray("comments");

        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            String author = j.getString("author");
            comments.add(new Comment(author, Utils.parseUrl(baseUrl,j.getString("author_thumbnail")),
                    j.getString("text"), Utils.parseRelativeDate(j.getString("published_at")), author));
        }

        int likes; try {
            likes = Integer.parseInt(json.getString("likes"));
        } catch(NumberFormatException ignored) { likes = -1; }
        String duration = json.getString("duration");
        int length; try {
            length = json.getInt("length_seconds");
        } catch(JSONException ignored) {
            length = parseToSeconds(duration);
        }
        String channelId; try {
            channelId = URLDecoder.decode(json.getString("channel_custom_url"), "UTF-8");
        } catch(NullPointerException ignored) { channelId = channel; }
        return new Video(id, title, Utils.parseUrl(baseUrl,json.getString("thumbnail")), channel,
                Utils.parseUrl(baseUrl,json.getString("channel_thumbnail")), channelId, duration, length,
                Long.parseLong(json.getString("views")), publishedAt, json.getString("description"), likes,
                Integer.parseInt(json.getString("subscriberCount")), baseUrl + "/direct_url?video_id=" + id, null, comments);
    }

    @Override
    public String getVideoUrl(String id, String quality, int ignored) throws IOException {
        return baseUrl + "/direct_url?video_id=" + id + "&quality=" + quality;
    }

    /** Conversion to MPEG-4 Visual (270p/360p) or H.263 */
    @Override
    public String getConvUrl(String id, Codec codec) {
        switch (codec) {
            case H263:
                return baseUrl + "/direct_url?video_id=" + id + "&codec=h263";
            default:
                return baseUrl + "/direct_url?video_id=" + id + "&codec=mpeg4";
        }
    }

    @Override public List<VideoInfo> getRelated(String id) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/get_related_videos.php").addParam("video_id", id).build();
        JSONArray arr = JSON.getArray(HttpClient.executeToString(req, 60000));
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject json = arr.getObject(i);
            long views; try {
                views = Long.parseLong(json.getString("views"));
            }catch(NumberFormatException ignored) { views = -1; }
            videos.add(new VideoInfo(json.getString("video_id"), json.getString("title"),
                    Utils.parseUrl(baseUrl, json.getString("thumbnail")), json.getString("author"),
                    Utils.parseUrl(baseUrl, json.getString("channel_thumbnail")), "", "",
                    views, Utils.parseRelativeDate(json.getString("published_at"))));
        }
        return videos;
    }

    @Override public List<Comment> getComments(String id) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/get-ytvideo-info.php").addParam("video_id", id).build();
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));

        List<Comment> comments = new ArrayList<Comment>();
        JSONArray arr = json.getArray("comments");

        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            String author = j.getString("author");
            comments.add(new Comment(author, Utils.parseUrl(baseUrl,j.getString("author_thumbnail")),
                    j.getString("text"), Utils.parseRelativeDate(j.getString("published_at")), author));
        }

        return comments;
    }

    @Override
    public String getThumbnail(String id) {
        return baseUrl + "/thumbnail/" + id;
    }

    @Override
    public Channel getChannel(String id) throws IOException {
        HttpRequest req;
        if (id.startsWith("UC"))
            req = new HttpRequest(baseUrl, "/get_author_videos_by_id.php?channel_id=" + id);
        else
            req = new HttpRequest(baseUrl, "/get_author_videos.php?author=" + id);
        JSONObject obj = JSON.getObject(HttpClient.executeToString(req));
        JSONArray arr = obj.getArray("videos");
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject json = arr.getObject(i);
            String duration; try {
                duration = json.getString("duration");
            } catch(JSONException ignored) { duration=null; }
            videos.add(new VideoInfo(json.getString("video_id"), json.getString("title"),
                    Utils.parseUrl(baseUrl, json.getString("thumbnail")), null, null, null,
                    duration, Long.parseLong(json.getString("views")), Utils.parseRelativeDate(json.getString("published_at"))));
        }
        JSONObject json = obj.getObject("channel_info");
        String thumbnail = json.getString("thumbnail");
        int subscribers; try {
            subscribers = Integer.parseInt(json.getString("subscriber_count"));
        } catch (Exception e) {
            e.printStackTrace(); subscribers = -1;
        }
        return new Channel(thumbnail.substring(thumbnail.lastIndexOf('/') + 1), json.getString("title"), Utils.parseUrl(baseUrl,json.getString("thumbnail")),
                Utils.parseUrl(baseUrl,json.getString("banner").replace("w2560", "w900")), json.getString("description"),
                subscribers, videos);
    }

    @Override
    public String getChannelIcon(String id) throws IOException {
        if (!id.startsWith("UC")) {
            HttpRequest req = new HttpRequest.Builder(baseUrl, "/get_author_videos.php").addParam("author", id).build();
            return Utils.parseUrl(baseUrl, JSON.getObject(HttpClient.executeToString(req)).getObject("channel_info").getString("thumbnail"));
        }
        return baseUrl + "/channel_icon/" + id;
    }

    @Override
    public List<VideoInfo> getChannelVideos(String id, int sort) throws IOException {
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        HttpRequest req = new HttpRequest(baseUrl, "/playlist/" + (sort == 0 ? "UU" : "UULP") + id.substring(2));
        JSONObject json = JSON.getObject(HttpClient.executeToString(req));
        try {
            String error = json.getString("error");
            if (error != null && error.length() > 0)
                throw new ContentUnavailableException(error);
        } catch (JSONException ignored) {}
        JSONArray arr = json.getArray("videos");
        for (int i = 0; i < arr.size(); i++) {
            JSONObject j = arr.getObject(i);
            Date publishedAt; try {
                publishedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(j.getString("published_at"));
            } catch(Exception ignored) { publishedAt = null; }
            long views; try {
                views = Long.parseLong(j.getString("views").replaceAll("[^0-9]", ""));
            }catch(Exception ignored) { views = -1; }
            videos.add(new VideoInfo(j.getString("video_id"), j.getString("title"), Utils.parseUrl(baseUrl,j.getString("thumbnail")),
                    null, null, null, "", views, publishedAt));
        }
        return videos;
    }

    /**
     * Translates an ISO 8601 duration string (e.g., "PT16M21S") into seconds.
     *
     * @param duration The duration string to parse.
     * @return The total length in seconds.
     * @throws IllegalArgumentException if the format is invalid.
     */
    private static int parseToSeconds(String duration) {
        if (duration == null || duration.length() == 0 || duration.charAt(0) != 'P') {
            return -1;
        }
        int totalSeconds = 0;
        int currentValue = 0;
        boolean hasValue = false;
        int startIndex = (duration.length() > 1 && duration.charAt(1) == 'T') ? 2 : 1;
        for (int i = startIndex; i < duration.length(); i++) {
            char c = duration.charAt(i);
            if (Character.isDigit(c)) {
                currentValue = currentValue * 10 + (c - '0');
                hasValue = true;
            } else {
                if (!hasValue) {
                    throw new IllegalArgumentException("Invalid duration: time unit '" + c + "' without a preceding value.");
                }
                switch (c) {
                    case 'D':
                        totalSeconds += currentValue * 86400;
                        break;
                    case 'H':
                        totalSeconds += currentValue * 3600;
                        break;
                    case 'M':
                        totalSeconds += currentValue * 60;
                        break;
                    case 'S':
                        totalSeconds += currentValue;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown time unit: '" + c + "'");
                }
                currentValue = 0;
                hasValue = false;
            }
        }

        return totalSeconds;
    }
}
