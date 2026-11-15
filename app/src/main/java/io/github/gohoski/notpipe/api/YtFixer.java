package io.github.gohoski.notpipe.api;

import java.io.IOException;
import java.util.List;

import io.github.gohoski.notpipe.data.Account;
import io.github.gohoski.notpipe.data.AuthPrompt;
import io.github.gohoski.notpipe.data.ChannelInfo;
import io.github.gohoski.notpipe.data.VideoInfo;

/**
 * Created by Gleb on 29.08.2026.
 * Stub implementation for YTFixer authentication.
 * The actual device-code flow is not yet implemented.
 */
public class YtFixer implements Auth {
    private String baseUrl;

    public YtFixer(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getName() { return "YTFixer"; }
    public String getHost() {
        return baseUrl.replace("https://", "").replace("http://", "");
    }

    @Override
    public AuthPrompt startAuth() throws IOException {
        throw new IOException("YTFixer login not yet implemented");
    }

    @Override
    public boolean checkAuthStatus(AuthPrompt prompt) throws IOException {
        throw new IOException("YTFixer login not yet implemented");
    }

    @Override
    public Account completeAuth(AuthPrompt prompt) throws IOException {
        throw new IOException("YTFixer login not yet implemented");
    }

    @Override
    public ChannelInfo getAuthChannel(Account account) throws IOException {
        return null;
    }

    @Override
    public List<VideoInfo> getHistory(Account account) throws IOException {
        return null;
    }

    @Override
    public List<VideoInfo> getRecommendations(Account account) throws IOException {
        throw new IOException("YTFixer not implemented");
    }

    @Override
    public List<VideoInfo> getSubscriptionsFeed(Account account) throws IOException {
        throw new IOException("YTFixer not implemented");
    }

    @Override
    public List<ChannelInfo> getSubscribedChannels(Account account) throws IOException {
        throw new IOException("YTFixer not implemented");
    }

    @Override
    public String addToHistory(Account account, String videoId) throws IOException {
        throw new IOException("YTFixer not implemented");
    }

    @Override
    public void updateWatchtime(Account account, String videoId, int time, String watchtimeToken, boolean isFinal) throws IOException {
        return;
    }

    @Override
    public int getRating(Account account, String videoId) {
        return 0;
    }

    @Override
    public void rateVideo(Account account, String videoId, int rating) throws IOException {
        throw new IOException("YTFixer not implemented");
    }

    @Override
    public List<VideoInfo> getPersonalizedRelated(Account account, String videoId) throws IOException {
        throw new IOException("YTFixer not implemented");
    }

    @Override
    public boolean isSubscribed(Account account, String channelId) throws IOException {
        throw new IOException("YTFixer not implemented");
    }

    @Override
    public void manageSubscription(Account account, String channelId, boolean isSubscribed) throws IOException {
        throw new IOException("YTFixer not implemented");
    }
}
