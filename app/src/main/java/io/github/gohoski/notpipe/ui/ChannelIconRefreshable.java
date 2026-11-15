package io.github.gohoski.notpipe.ui;

import android.view.View;

/**
 * Implemented by adapters whose rows display channel icons
 * that can be refreshed once a channel icon has been resolved by ID.
 */
public interface ChannelIconRefreshable {

    /**
     * Re-checks and reloads the channel icon for the given row
     * if it belongs to the specified channel.
     *
     * @param row the row view to update
     * @param channelId the channel ID whose icon has been resolved
     */
    void updateChannelIconForView(View row, String channelId);
}
