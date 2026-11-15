package io.github.gohoski.notpipe.ui;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import io.github.gohoski.notpipe.R;
import io.github.gohoski.notpipe.Utils;
import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.util.ChannelIconResolver;
import io.github.gohoski.notpipe.util.ImageLoader;

/**
 * Adapter for video lists with lazy loading support.
 * Only loads images for items that have been explicitly marked as visible.
 */
public class VideoAdapter extends ArrayAdapter<VideoInfo> implements ChannelIconRefreshable {
    private final Context context;
    private final int layoutResource;
    private final boolean isFullLayout;
    private final boolean isCompactLayout;
    private final boolean isNoIconLayout;
    private final LayoutInflater inflater;
    private final boolean isInChannelActivity;
    private final int itemHorizontalPadding;
    private final int gridItemHeight;
    private ChannelIconResolver.OnIconResolvedListener iconResolveListener;

    public VideoAdapter(Context context, int resource, List<VideoInfo> objects) {
        this(context, resource, objects, false);
    }

    public VideoAdapter(Context context, int resource, List<VideoInfo> objects, boolean isInChannelActivity) {
        super(context, resource, objects);
        this.context = context;
        this.layoutResource = resource;
        this.isFullLayout = resource == R.layout.item_video;
        this.isCompactLayout = resource == R.layout.item_video_compact;
        this.isNoIconLayout = resource == R.layout.item_video_noicon;
        this.inflater = ((Activity) context).getLayoutInflater();
        this.isInChannelActivity = isInChannelActivity;
        this.itemHorizontalPadding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics());
        this.gridItemHeight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, this.isFullLayout ? 250 : 220, context.getResources().getDisplayMetrics());
    }

    public void setChannelIconResolveListener(ChannelIconResolver.OnIconResolvedListener listener) {
        this.iconResolveListener = listener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View row = convertView;

        boolean isLazyLoading = parent instanceof AdapterLinearLayout;

        if (row == null) {
            row = inflater.inflate(layoutResource, parent, false);
            holder = new ViewHolder();
            holder.videoTitle = (TextView) row.findViewById(R.id.video_title);
            holder.channelTitle = (TextView) row.findViewById(R.id.channel_title);
            holder.videoThumb = (ImageView) row.findViewById(R.id.video_thumbnail);
            holder.channelThumb = (ImageView) row.findViewById(R.id.channel_thumbnail);
            holder.duration = (TextView) row.findViewById(R.id.duration);
            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        // Apply padding and height dynamically based on parent type:
        // ListView/AdapterLinearLayout stays wrap_content with padding.
        // GridView (landscape tablets) gets fixed height to stop jitter and 0 horizontal padding.
        if (parent instanceof ListView) {
            row.setPadding(itemHorizontalPadding, row.getPaddingTop(), itemHorizontalPadding, row.getPaddingBottom());
        } else if (parent instanceof GridView) {
            row.setPadding(0, row.getPaddingTop(), 0, row.getPaddingBottom());
            ViewGroup.LayoutParams lp = row.getLayoutParams();
            if (lp != null && lp.height != gridItemHeight && !isInChannelActivity) {
                lp.height = gridItemHeight;
                row.setLayoutParams(lp);
            }
        }

        final VideoInfo video = getItem(position);

        holder.videoTitle.setMaxLines((isCompactLayout || isNoIconLayout) && (video.views <= 0 || video.publishedAt == null) ? 3 : 2);
        holder.videoTitle.setText(video.title);
        holder.channelTitle.setText(buildMetaText(video));

        if (holder.duration != null) {
            if (video.duration != null && video.duration.length() > 0) {
                holder.duration.setText(video.duration);
                holder.duration.setVisibility(View.VISIBLE);
            } else {
                holder.duration.setVisibility(View.GONE);
            }
        }

        holder.video = video;
        holder.imagesLoaded = false;
        ImageLoader.cancelLoad(holder.videoThumb);
        holder.videoThumb.setImageBitmap(null);
        holder.videoThumb.setTag(video.thumbnail);
        if (holder.channelThumb != null) {
            ImageLoader.cancelLoad(holder.channelThumb);
            holder.channelThumb.setImageBitmap(null);
        }

        // If we are in MainActivity (standard ListView), load images immediately.
        // Recycled views are guarded by ImageView tags, so a late decode never
        // lands in the wrong row.
        if (!isLazyLoading) {
            ImageLoader.loadImage(video.thumbnail, holder.videoThumb);
            loadChannelIcon(holder);
            holder.imagesLoaded = true;
        }

        return row;
    }

    /**
     * Builds the secondary text of a row: channel name, view count and publish date.
     * Views and date are shown independently whenever either is available.
     */
    private String buildMetaText(VideoInfo video) {
        String ago = video.publishedAt == null ? null : Utils.formatTimeAgo(context, video.publishedAt);

        if (isCompactLayout || isNoIconLayout) {
            String views = video.views > 0 ? Utils.formatNumber(context, video.views) : null;
            if (isInChannelActivity) {
                return join("\n", views == null ? null : context.getString(R.string.views, views), ago);
            }
            String meta = join(" · ", views, ago);
            return meta.length() > 0 ? video.channel + "\n" + meta : video.channel;
        }

        if (isFullLayout) {
            String views = video.views > 0 ? context.getString(R.string.views, Utils.formatNumber(context, video.views)) : null;
            return join(" · ", video.channel, views, ago);
        }

        return video.channel;
    }

    /**
     * Concatenates non-empty parts, inserting the separator between them.
     */
    private static String join(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null || parts[i].length() == 0) continue;
            if (sb.length() > 0) sb.append(separator);
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /**
     * Called by VideoActivity's ScrollView listener when this row becomes visible.
     */
    public void loadImagesForView(View row) {
        if (row.getTag() instanceof ViewHolder) {
            ViewHolder holder = (ViewHolder) row.getTag();
            if (!holder.imagesLoaded && holder.video != null) {
                ImageLoader.loadImage(holder.video.thumbnail, holder.videoThumb);
                loadChannelIcon(holder);
                holder.imagesLoaded = true;
            }
        }
    }

    /**
     * Specifically re-checks and loads the channel icon for a given row.
     * This is called by VideoActivity when a fallback URL has been successfully resolved.
     */
    @Override
    public void updateChannelIconForView(View row, String resolvedChannelId) {
        if (row != null && row.getTag() instanceof ViewHolder) {
            ViewHolder holder = (ViewHolder) row.getTag();
            if (holder.video != null && holder.imagesLoaded && resolvedChannelId.equals(holder.video.channelId)) {
                loadChannelIcon(holder);
            }
        }
    }

    /**
     * Loads the channel icon through the shared resolver, which handles
     * resolved URLs and background fallback fetching automatically.
     */
    private void loadChannelIcon(ViewHolder holder) {
        if (holder.channelThumb == null || holder.video == null) return;
        ChannelIconResolver.loadInto(holder.channelThumb, holder.video.channelId, holder.video.channelThumbnail, iconResolveListener);
    }

    /**
     * Reset image state for a view so images are reloaded on next visibility check.
     * Pending loads for the recycled views are cancelled as well.
     */
    public void resetImageView(View row) {
        if (row != null && row.getTag() instanceof ViewHolder) {
            ViewHolder holder = (ViewHolder) row.getTag();
            if (holder.imagesLoaded) {
                holder.imagesLoaded = false;
                if (holder.videoThumb != null) {
                    ImageLoader.unbindImageView(holder.videoThumb);
                }
                if (holder.channelThumb != null) {
                    ImageLoader.unbindImageView(holder.channelThumb);
                }
            }
        }
    }

    private static class ViewHolder {
        TextView videoTitle;
        TextView channelTitle;
        TextView duration;
        ImageView videoThumb;
        ImageView channelThumb;
        VideoInfo video;
        boolean imagesLoaded;
    }
}