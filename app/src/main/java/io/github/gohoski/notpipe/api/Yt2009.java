package io.github.gohoski.notpipe.api;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.notpipe.Utils;
import io.github.gohoski.notpipe.auth.SubscriptionCache;
import io.github.gohoski.notpipe.data.Account;
import io.github.gohoski.notpipe.data.AuthPrompt;
import io.github.gohoski.notpipe.data.ChannelInfo;
import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.http.HttpClient;
import io.github.gohoski.notpipe.http.HttpException;
import io.github.gohoski.notpipe.http.HttpRequest;

/**
 * Implementation of yt2009's video endpoints
 * yt2009 isn't quite designed to be used as an API—it's more a frontend, however the video endpoints and HTML are straightforward
 */
public class Yt2009 implements VideoStream, Conversion, ChannelApi, Auth {
    private String baseUrl;

    public Yt2009(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getName() { return "yt2009"; }
    public String getHost() {
        return baseUrl.replace("https://", "").replace("http://", "");
    }

    @Override
    public String getVideoUrl(String id, String quality, int timeout) throws IOException {
        String url;
        switch(quality) {
            case "480":
                url = "/get_480?video_id=" + id; break;
            case "720":
                url = "/exp_hd?video_id=" + id; break;
            case "1080":
                url = "/exp_hd?video_id=" + id + "&fhd=1"; break;
            default:
                url = "/channel_fh264_getvideo?v=" + id;
        }
        return HttpClient.getRedirectUrl(baseUrl, url, timeout);
    }

    @Override
    public String getConvUrl(String id, Codec codec) throws IOException {
        String path;
        switch (codec) {
            case MPEG4:
                path = "/http_mpeg4?v="; break;
            case MPEG4_360:
                path = "/http_mpeg4_360?v="; break;
            default:
                path = "/http_3gp_288?v=";
        }
        return HttpClient.getRedirectUrl(baseUrl, path + id, HttpClient.CONVERSION_TIMEOUT);
    }

    @Override
    public List<VideoInfo> getChannelVideos(String id, int sort) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/channel_sort")
                .addHeader("source", id)
                .addHeader("sort", sort == 1 ? "popularity" : "date").build();
        String html = HttpClient.executeToString(req);
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        Pattern pattern = Pattern.compile(
                "<div\\s+class=\"playnav-item playnav-video\\s*\"\\s+id=\"playnav-video-([a-zA-Z0-9_-]+)\".*?" +
                        "<span\\s+class=\"video-title-[a-zA-Z0-9_-]+\">([^<]+)</span>.*?" +
                        "<div\\s+class=\"metadata video-meta-[a-zA-Z0-9_-]+\">([^<]+)</div>",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String videoId = matcher.group(1);
            String title = Utils.decodeHtmlEntities(matcher.group(2));
            String rawMetadata = matcher.group(3);

            long views = -1;
            Date date = null;

            if (rawMetadata != null) {
                int delimiterIndex = rawMetadata.indexOf(" - ");
                if (delimiterIndex != -1) {
                    views = Long.parseLong(rawMetadata.substring(0, delimiterIndex).replaceAll("[^0-9]", ""));
                    date = Utils.parseRelativeDate(rawMetadata.substring(delimiterIndex + 3));
                } else {
                    views = Long.parseLong(rawMetadata.replaceAll("[^0-9]", ""));
                }
            }

            videos.add(new VideoInfo(videoId, title.trim(), baseUrl + "/thumb_proxy?v=" + videoId, null, null, null, null, views, date));
        }
        return videos;
    }

    /*** Authorization endpoints **/

    @Override
    public AuthPrompt startAuth() throws IOException {
        HttpRequest createReq = new HttpRequest(baseUrl, "/create_pchelper", "POST");
        String createResp = HttpClient.executeToString(createReq);

        String deviceId = null;
        String[] params = createResp.split("&");
        for (int i = 0; i < params.length; i++) {
            String[] pair = params[i].split("=");
            if (pair.length == 2 && "pchelper_user".equals(pair[0])) {
                deviceId = pair[1].trim();
                break;
            }
        }

        if (deviceId == null || deviceId.length() == 0) {
            throw new IOException("Failed to obtain pchelper_user from yt2009");
        }

        HttpRequest gsignReq = new HttpRequest.Builder(baseUrl, "/gsign")
                .addParam("device", deviceId)
                .addHeader("User-Agent", "Android " + HttpClient.USER_AGENT)
                .build();
        HttpClient.execute(gsignReq).close();

        return new AuthPrompt(
                baseUrl,
                deviceId,
                baseUrl + "/gsign",
                io.github.gohoski.notpipe.R.string.auth_desc_yt2009,
                null,
                deviceId
        );
    }

    @Override
    public boolean checkAuthStatus(AuthPrompt prompt) throws IOException {
        HttpRequest statusReq = new HttpRequest.Builder(baseUrl, "/pull_pchelper")
                .addHeader("x-gdata-device", "device-id=\"" + prompt.rawPayload + "\"")
                .build();
        InputStream in = null;
        try {
            in = HttpClient.execute(statusReq);
            return true;
        } catch (HttpException e) {
            if (e.getStatusCode() == 401)
                return false;
            throw e;
        } catch (IOException e) {
            // 401 pending
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("authentication challenge")) {
                return false;
            }
            throw e;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Override
    public Account completeAuth(AuthPrompt prompt) throws IOException {
        return new Account(
                Account.TYPE_YT2009,
                baseUrl,
                prompt.rawPayload
        );
    }

    @Override
    public List<VideoInfo> getRecommendations(Account account) throws IOException {
        if (account == null || account.token == null)
            return new ArrayList<VideoInfo>();

        HttpRequest req = new HttpRequest.Builder(baseUrl, "/yt2009_recommended")
                .addHeader("Cookie", "pchelper_flags=default_w2w; pchelper_user=" + account.token)
                .addHeader("source", "recommended_page")
                .build();

        String html = HttpClient.executeToString(req);
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        Pattern pattern = Pattern.compile(
                "<div\\s+class=\"video-cell[^\"]*\"[^>]*data-id=\"([^\"]+)\".*?" +
                        "<div\\s+class=\"video-time\">\\s*<a[^>]*>([^<]+)</a>\\s*</div>.*?" +
                        "<div\\s+class=\"video-short-title\">\\s*<a[^>]*>([^<]+)</a>.*?" +
                        "<span[^>]*class=\"video-view-count\">([^<]+)</span>.*?" +
                        "<span\\s+class=\"video-username\">\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>",
                Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String videoId = matcher.group(1);
            String duration = matcher.group(2).trim();
            String title = Utils.decodeHtmlEntities(matcher.group(3)).trim();
            String viewStr = matcher.group(4);
            String channelHref = matcher.group(5);
            String channelName = Utils.decodeHtmlEntities(matcher.group(6)).trim();

            long views = -1;
            if (viewStr != null) {
                try {
                    String numOnly = viewStr.replaceAll("[^0-9]", "");
                    if (numOnly.length() > 0) {
                        views = Long.parseLong(numOnly);
                    }
                } catch (NumberFormatException ignored) {}
            }

            String channelId = null;
            if (channelHref != null) {
                int lastSlash = channelHref.lastIndexOf('/');
                if (lastSlash != -1 && lastSlash + 1 < channelHref.length()) {
                    channelId = channelHref.substring(lastSlash + 1);
                } else {
                    channelId = channelHref;
                }
            }

            videos.add(new VideoInfo(
                    videoId,
                    title,
                    getThumbnail(videoId),
                    channelName,
                    getChannelIcon(channelId),
                    channelId,
                    duration,
                    views,
                    null
            ));
        }

        return videos;
    }

    public String getChannelIcon(String id) {
        if (id == null || id.length() == 0) return null;
        return baseUrl + "/minipicty?channel=" + id;
    }

    public String getThumbnail(String id) {
        return baseUrl + "/thumb_proxy?v=" + id;
    }

    @Override
    public List<VideoInfo> getSubscriptionsFeed(Account account) throws IOException {
        if (account == null || account.token == null) {
            return new ArrayList<VideoInfo>();
        }
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/homepage_subscriptions", "POST")
                .addHeader("x-gdata-device", "device-id=\"" + account.token + "\"")
                .build();
        String html = HttpClient.executeToString(req);
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        Pattern pattern = Pattern.compile(
                "<div\\s+class=\"video-cell\"[^>]*data-id=\"([^\"]+)\".*?" +
                        "<div\\s+class=\"video-time\">\\s*<a[^>]*>([^<]+)</a>\\s*</div>.*?" +
                        "<div\\s+class=\"video-short-title\">\\s*<a[^>]*>([^<]+)</a>.*?" +
                        "<span[^>]*class=\"video-date-added\">([^<]+)</span>.*?" +
                        "<span[^>]*class=\"video-view-count\">([^<]+)</span>.*?" +
                        "<span\\s+class=\"video-username\">\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String videoId = matcher.group(1);
            String duration = matcher.group(2).trim();
            String title = Utils.decodeHtmlEntities(matcher.group(3)).trim();
            String dateStr = matcher.group(4);
            String viewStr = matcher.group(5);
            String channelHref = matcher.group(6);
            String channelName = Utils.decodeHtmlEntities(matcher.group(7)).trim();
            Date published = null;
            if (dateStr != null) {
                published = Utils.parseRelativeDate(dateStr.trim());
            }
            long views = -1;
            if (viewStr != null) {
                try {
                    String numOnly = viewStr.replaceAll("[^0-9]", "");
                    if (numOnly.length() > 0) {
                        views = Long.parseLong(numOnly);
                    }
                } catch (NumberFormatException ignored) {}
            }
            String channelId = null;
            if (channelHref != null) {
                int lastSlash = channelHref.lastIndexOf('/');
                if (lastSlash != -1 && lastSlash + 1 < channelHref.length()) {
                    channelId = channelHref.substring(lastSlash + 1);
                } else {
                    channelId = channelHref;
                }
            }
            videos.add(new VideoInfo(
                    videoId,
                    title,
                    getThumbnail(videoId),
                    channelName,
                    getChannelIcon(channelId),
                    channelId,
                    duration,
                    views,
                    published
            ));
        }
        return videos;
    }

    @Override
    public List<ChannelInfo> getSubscribedChannels(Account account) throws IOException {
        List<ChannelInfo> channels = new ArrayList<ChannelInfo>();
        if (account == null || account.token == null) {
            return channels;
        }
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/pchelper_subs?format=localstorage")
                .addHeader("x-gdata-device", "device-id=\"" + account.token + "\"")
                .build();
        JSONArray arr = JSON.getArray(HttpClient.executeToString(req));
        for (int i = 0; i < arr.size(); i++) {
            JSONObject json = arr.getObject(i);
            String id = json.getString("url").substring(9);
            channels.add(new ChannelInfo(id, json.getString("creator"), getChannelIcon(id)));
        }
        try {
            SubscriptionCache.put(account, channels);
        } catch (Exception ignored) {}
        return channels;
    }

    @Override
    public ChannelInfo getAuthChannel(Account account) throws IOException {
        if (account == null || account.token == null || account.token.length() == 0) {
            return null;
        }
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/gsign_get_devices")
                .addHeader("Cookie", "pchelper_user=" + account.token)
                .build();
        String html = HttpClient.executeToString(req);
        Pattern rowPattern = Pattern.compile("<tr[^>]*class=\"account\"[^>]*>(.*?)</tr>", Pattern.DOTALL);
        Matcher rowMatcher = rowPattern.matcher(html);
        while (rowMatcher.find()) {
            String inner = rowMatcher.group(1);
            if (inner.indexOf("selected-label") == -1)
                continue;
            String title = null;
            Matcher nameMatcher = Pattern.compile("<span[^>]*id=\"account-name\"[^>]*>([^<]+)</span>").matcher(inner);
            if (nameMatcher.find())
                title = Utils.decodeHtmlEntities(nameMatcher.group(1)).trim();
            String thumbnail = null;
            Matcher imgMatcher = Pattern.compile("<img[^>]*src=\"([^\"]+)\"").matcher(inner);
            if (imgMatcher.find()) {
                thumbnail = imgMatcher.group(1).trim();
                if (thumbnail.startsWith("/"))
                    thumbnail = baseUrl + thumbnail;
            }
            if (thumbnail == null || thumbnail.length() == 0)
                thumbnail = getChannelIcon(title);
            if (title != null && title.length() > 0)
                return new ChannelInfo(title, title, thumbnail);
            return null;
        }
        return null;
    }

    @Override
    public List<VideoInfo> getHistory(Account account) throws IOException {
        List<VideoInfo> videos = new ArrayList<VideoInfo>();
        if (account == null || account.token == null) {
            return videos;
        }
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/my_history")
                .addHeader("Cookie", "pchelper_flags=use_yt_history; pchelper_user=" + account.token)
                .build();
        String html = HttpClient.executeToString(req);
        Pattern pattern = Pattern.compile(
                "<input[^>]*class=\"checkbox\"[^>]*value=\"([^\"]+)\"[^>]*>.*?" +
                        "<a[^>]*class=\"title\"[^>]*>([^<]+)</a>.*?" +
                        "<span\\s+class=\"stat-views\">([^<]+)</span>",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String videoId = matcher.group(1);
            String title = Utils.decodeHtmlEntities(matcher.group(2)).trim();
            String viewStr = matcher.group(3);
            long views = -1;
            if (viewStr != null) {
                try {
                    views = Long.parseLong(viewStr.replaceAll("[^0-9]", ""));
                } catch (NumberFormatException ignored) {}
            }
            videos.add(new VideoInfo(
                    videoId, title,
                    getThumbnail(videoId),
                    null, null, null, null,
                    views, null
            ));
        }
        return videos;
    }

    @Override
    public int getRating(Account account, String videoId) {
        // Unfortunately, yt2009 has yet to provide an interface for retrieving if the video has been liked or not
        // Right now there isn't even any real like count implemented in yt2009, it is intentionally random numbers
        // (probably because they are needed to be provided beforehand for GData).
        // I'm not blaming yt2009 because it's a frontend, but a like count would be good for the experience in general.
        return 0;
    }

    @Override
    public void rateVideo(Account account, String videoId, int rating) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/feeds/api/videos/" + videoId + "/ratings", "POST")
                .addHeader("x-gdata-device", "device-id=\"" + account.token + "\"")
                .body(rating == 1 ? "like" : "dislike") // yt2009 doesn't have a "none" option…
                .build();
        InputStream in = null;
        try {
            in = HttpClient.execute(req);
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Override
    public String addToHistory(Account account, String videoId) throws IOException {
        if (account == null || account.token == null || videoId == null || videoId.length() == 0) {
            return null;
        }
        String cookie = "watch_flags=exp_sabr; saber_playback_mode_picked=1; pchelper_user="
                + account.token
                + "; pchelper_flags=comments_add_youtube:commit_ratings:default_w2w:playlists_sync:subscriptions_sync:user_related:use_yt_history:watchlater_sync:with_pchelper_watch";

        HttpRequest watchReq = new HttpRequest.Builder(baseUrl, "/watch")
                .addParam("v", videoId)
                .addHeader("Cookie", cookie)
                .build();

        String html = HttpClient.executeToString(watchReq);
        String playbackId = null;
        Matcher pbMatcher = Pattern.compile("playbackIdShorthand\\s*=\\s*[\"']([^\"']+)[\"']").matcher(html);
        if (pbMatcher.find())
            playbackId = pbMatcher.group(1);
        String watchtimeId = null;
        Matcher wtMatcher = Pattern.compile("watchtimeIdShorthand\\s*=\\s*[\"']([^\"']+)[\"']").matcher(html);
        if (wtMatcher.find())
            watchtimeId = wtMatcher.group(1);

        if (playbackId != null) {
            HttpRequest reportReq = new HttpRequest.Builder(baseUrl, "/playback_progress_report")
                    .addParam("pid", playbackId)
                    .addParam("rt", String.valueOf(System.currentTimeMillis()))
                    .addHeader("Cookie", cookie)
                    .build();
            InputStream in = null;
            try {
                in = HttpClient.execute(reportReq);
                return watchtimeId;
            } finally {
                if (in != null) {
                    try { in.close(); } catch (IOException ignored) {}
                }
            }
        }

        return null;
    }

    @Override
    public void updateWatchtime(Account account, String videoId, int time, String watchtimeToken, boolean isFinal) throws IOException {
        if (watchtimeToken == null) return;
        HttpRequest.Builder req = new HttpRequest.Builder(baseUrl, "/playback_progress_report")
                .addParam("pid", watchtimeToken)
                .addParam("state", isFinal ? "paused" : "playing")
                .addParam("rt", String.valueOf(System.currentTimeMillis()))
                .addParam("st", String.valueOf(/*Math.round(*/ time / 1000.0f /*) / 10.0f*/))
                .addHeader("Cookie", "watch_flags=exp_sabr; saber_playback_mode_picked=1; pchelper_user="
                        + account.token
                        + "; pchelper_flags=comments_add_youtube:commit_ratings:default_w2w:playlists_sync:subscriptions_sync:user_related:use_yt_history:watchlater_sync:with_pchelper_watch");
        if (isFinal) req.addParam("final", "1");
        InputStream in = null;
        try {
            in = HttpClient.execute(req.build());
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Override
    public boolean isSubscribed(Account account, String channelId) throws IOException {
        if (account == null || channelId == null || channelId.length() == 0) {
            return false;
        }
        try {
            Boolean cached = SubscriptionCache.contains(account, channelId);
            if (cached != null)
                return cached;
        } catch (Exception ignored) {}
        List<ChannelInfo> channels = getSubscribedChannels(account);
        for (int i = 0; i < channels.size(); i++) {
            ChannelInfo info = channels.get(i);
            if (info != null && channelId.equals(info.id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void manageSubscription(Account account, String channelId, boolean isSubscribed) throws IOException {
        HttpRequest req = new HttpRequest.Builder(baseUrl, "/feeds/api/users/default/subscriptions", "POST")
                .addHeader("x-gdata-device", "device-id=\"" + account.token + "\"")
                .body("<yt:username>" + channelId + "</yt:username>\n" + (isSubscribed ? "" : "<yt:unsubscribe/>")).build();
        HttpClient.execute(req).close();
        try {
            SubscriptionCache.update(account, channelId, isSubscribed);
        } catch (Exception ignored) {}
    }

    /**
     * Fetches personalized related videos for a given video ID using yt2009's GData feed.
     *
     * @param account The user account (optional; sends device-id header if present)
     * @param id The ID of the video to fetch related videos for
     * @return List of related VideoInfo items
     */
    public List<VideoInfo> getPersonalizedRelated(Account account, String id) throws IOException {
        if (id == null || id.length() == 0) {
            return new ArrayList<VideoInfo>();
        }

        HttpRequest.Builder builder = new HttpRequest.Builder(baseUrl, "/feeds/api/videos/" + id + "/related");
        if (account != null && account.token != null && account.token.length() > 0) {
            builder.addHeader("x-gdata-device", "device-id=\"" + account.token + "\"");
        }

        InputStream in = null;
        try {
            in = HttpClient.execute(builder.build());
            List<VideoInfo> videos = new ArrayList<VideoInfo>();
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, null);

            boolean inEntry = false;
            String videoId = null, title = null, authorName = null, authorId = null, duration = null;
            long views = -1;
            Date published = null;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (tag != null) {
                        int colon = tag.indexOf(':');
                        String localName = colon != -1 ? tag.substring(colon + 1) : tag;

                        if ("entry".equals(localName)) {
                            inEntry = true;
                            videoId = null;
                            title = null;
                            authorName = null;
                            authorId = null;
                            duration = null;
                            views = -1;
                            published = null;
                        } else if (inEntry) {
                            switch (localName) {
                                case "youTubeId":
                                case "videoid":
                                    if (videoId == null) {
                                        String idAttr = parser.getAttributeValue(null, "id");
                                        if (parser.isEmptyElementTag()) {
                                            videoId = idAttr;
                                        } else {
                                            String text = parser.nextText();
                                            videoId = (text != null && text.length() > 0) ? text.trim() : idAttr;
                                        }
                                    }
                                    break;

                                case "id":
                                    if (videoId == null) {
                                        String text = parser.nextText();
                                        if (text != null) {
                                            int lastSlash = text.lastIndexOf('/');
                                            if (lastSlash != -1 && lastSlash + 1 < text.length()) {
                                                videoId = text.substring(lastSlash + 1).trim();
                                            }
                                        }
                                    }
                                    break;

                                case "title":
                                    if (title == null || title.length() == 0) {
                                        title = parser.nextText().trim();
                                    }
                                    break;

                                case "yt9full":
                                    String full = parser.nextText();
                                    if (full != null && full.trim().length() > 0) {
                                        authorName = full.trim();
                                    }
                                    break;

                                case "name":
                                    if (authorName == null) {
                                        authorName = parser.nextText().trim();
                                    }
                                    break;

                                case "credit":
                                    if (authorName == null) {
                                        String display = parser.getAttributeValue(null, "display");
                                        if (display == null) {
                                            display = parser.getAttributeValue(null, "yt:display");
                                        }
                                        if (display != null && display.trim().length() > 0) {
                                            authorName = display.trim();
                                        }
                                    }
                                    break;

                                case "yt9aid":
                                    String aid = parser.nextText();
                                    if (aid != null && aid.trim().length() > 0) {
                                        authorId = aid.trim();
                                    }
                                    break;

                                case "userId":
                                case "uploaderId":
                                    if (authorId == null) {
                                        authorId = parser.nextText().trim();
                                    }
                                    break;

                                case "duration":
                                    String secStr = parser.getAttributeValue(null, "seconds");
                                    if (secStr != null) {
                                        try {
                                            duration = Utils.formatDuration(Integer.parseInt(secStr.trim()));
                                        } catch (NumberFormatException ignored) {}
                                    }
                                    break;

                                case "statistics":
                                    String viewStr = parser.getAttributeValue(null, "viewCount");
                                    if (viewStr != null) {
                                        try {
                                            views = Long.parseLong(viewStr.trim());
                                        } catch (NumberFormatException ignored) {}
                                    }
                                    break;

                                case "published":
                                case "uploaded":
                                    if (published == null) {
                                        published = parseIsoDate(parser.nextText());
                                    }
                                    break;
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if (tag != null) {
                        int colon = tag.indexOf(':');
                        String localName = colon != -1 ? tag.substring(colon + 1) : tag;

                        if ("entry".equals(localName)) {
                            inEntry = false;
                            if (videoId != null && videoId.length() > 0) {
                                videos.add(new VideoInfo(
                                        videoId, title,
                                        getThumbnail(videoId),
                                        authorName,
                                        getChannelIcon(authorId),
                                        authorId,
                                        duration, views, published
                                ));
                            }
                        }
                    }
                }
                eventType = parser.next();
            }

            return videos;
        } catch (XmlPullParserException e) {
            throw new IOException("XML parse error: " + e.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Parses an ISO 8601 UTC date string (e.g. "2026-09-25T14:20:49.284Z")
     * without SimpleDateFormat allocation overhead.
     */
    private static Date parseIsoDate(String s) {
        if (s == null || s.length() < 10) return null;
        try {
            int year = Integer.parseInt(s.substring(0, 4));
            int month = Integer.parseInt(s.substring(5, 7)) - 1;
            int day = Integer.parseInt(s.substring(8, 10));

            int hour = 0;
            int min = 0;
            int sec = 0;

            if (s.length() >= 19 && (s.charAt(10) == 'T' || s.charAt(10) == ' ')) {
                hour = Integer.parseInt(s.substring(11, 13));
                min = Integer.parseInt(s.substring(14, 16));
                sec = Integer.parseInt(s.substring(17, 19));
            }

            java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            cal.clear();
            cal.set(year, month, day, hour, min, sec);
            return cal.getTime();
        } catch (Exception ignored) {
            return null;
        }
    }
}