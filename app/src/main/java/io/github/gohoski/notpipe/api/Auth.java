package io.github.gohoski.notpipe.api;

import java.io.IOException;
import java.util.List;
import io.github.gohoski.notpipe.data.Account;
import io.github.gohoski.notpipe.data.AuthPrompt;
import io.github.gohoski.notpipe.data.ChannelInfo;
import io.github.gohoski.notpipe.data.VideoInfo;

/**
 * Created by Gleb on 27.08.2026.
 */
public interface Auth {
    /**
     * Starts the interactive login flow
     */
    AuthPrompt startAuth() throws IOException;

    /**
     * Checks login progress.
     */
    boolean checkAuthStatus(AuthPrompt prompt) throws IOException;

    /**
     * Creates the Account data object once status is Success
     */
    Account completeAuth(AuthPrompt prompt) throws IOException;

    /// API operations

    List<VideoInfo> getRecommendations(Account account) throws IOException;
    List<VideoInfo> getSubscriptionsFeed(Account account) throws IOException;
    List<ChannelInfo> getSubscribedChannels(Account account) throws IOException;
    ChannelInfo getAuthChannel(Account account) throws IOException;
    List<VideoInfo> getHistory(Account account) throws IOException;

    /**
     * Adds video to history and marks it as started to being watched
     * @return A watchtime token to be passed into updateWatchtime, return null if unneeded
     */
    String addToHistory(Account account, String videoId) throws IOException;

    /**
     * Updates watch time for video, if not implemented on the side of the API return immediately
     * @param watchtimeToken Watchtime token returned from addToHistory, can be null if unneeded
     * @param time milliseconds
     * @param isFinal is this the final request
     */
    void updateWatchtime(Account account, String videoId, int time, String watchtimeToken, boolean isFinal) throws IOException;

    /**
     * Did the user like a video or not
     * @return 1 = liked, -1 = disliked, 0 = neither or N/A
     */
    int getRating(Account account, String videoId);
    void rateVideo(Account account, String videoId, int rating) throws IOException;

    List<VideoInfo> getPersonalizedRelated(Account account, String videoId) throws IOException;

    boolean isSubscribed(Account account, String channelId) throws IOException;
    void manageSubscription(Account account, String channelId, boolean isSubscribed) throws IOException;
}