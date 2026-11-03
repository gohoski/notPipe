package io.github.gohoski.notpipe.ui;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import io.github.gohoski.notpipe.R;
import io.github.gohoski.notpipe.Utils;
import io.github.gohoski.notpipe.data.Comment;
import io.github.gohoski.notpipe.util.ChannelIconResolver;
import io.github.gohoski.notpipe.util.ImageLoader;

/**
 * Adapter for comments with lazy loading support.
 * Only loads images for items that have been explicitly marked as visible.
 */
public class CommentAdapter extends ArrayAdapter<Comment> implements ChannelIconRefreshable {
    private Context context;
    private ChannelIconResolver.OnIconResolvedListener iconResolveListener;
    private OnChannelClickListener channelClickListener;

    public CommentAdapter(Context context, int resource, List<Comment> objects) {
        super(context, resource, objects);
        this.context = context;
    }

    public void setChannelIconResolveListener(ChannelIconResolver.OnIconResolvedListener listener) {
        this.iconResolveListener = listener;
    }

    public void setOnChannelClickListener(OnChannelClickListener listener) {
        this.channelClickListener = listener;
    }

    /**
     * Called when the user clicks the comment author's name or avatar.
     */
    public interface OnChannelClickListener {
        void onChannelClick(String channelId);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View row = convertView;

        if (row == null) {
            LayoutInflater inflater = ((Activity) context).getLayoutInflater();
            row = inflater.inflate(R.layout.item_comment, parent, false);
            holder = new ViewHolder();
            holder.channelTitle = (TextView) row.findViewById(R.id.channel_title);
            holder.content = (TextView) row.findViewById(R.id.content);
            holder.channelThumb = (ImageView) row.findViewById(R.id.channel_thumbnail);
            holder.date = (TextView) row.findViewById(R.id.date);
            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        Comment comment = getItem(position);
        holder.channelTitle.setText(comment.channel);
        holder.content.setText(comment.content);
        holder.date.setText(Utils.formatTimeAgo(context, comment.publishedAt));

        // Prepare holder for lazy loading
        holder.comment = comment;
        holder.imagesLoaded = false;

        // Reset thumbnail to prevent recycled view artifacts
        ImageLoader.cancelLoad(holder.channelThumb);
        holder.channelThumb.setImageBitmap(null);

        final String commentChannelId = comment.channelId;
        View.OnClickListener channelClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (channelClickListener != null && commentChannelId != null && commentChannelId.length() > 0) {
                    channelClickListener.onChannelClick(commentChannelId);
                }
            }
        };
        holder.channelTitle.setOnClickListener(channelClick);
        holder.channelThumb.setOnClickListener(channelClick);

        return row;
    }

    /**
     * Called by VideoActivity when this row enters the screen.
     */
    public void loadImagesForView(View row) {
        if (row.getTag() instanceof ViewHolder) {
            ViewHolder holder = (ViewHolder) row.getTag();
            if (!holder.imagesLoaded && holder.comment != null) {
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
            if (holder.comment != null && holder.imagesLoaded && resolvedChannelId.equals(holder.comment.channelId)) {
                loadChannelIcon(holder);
            }
        }
    }

    /**
     * Loads the channel icon through the shared resolver, which handles
     * resolved URLs and background fallback fetching automatically.
     */
    private void loadChannelIcon(ViewHolder holder) {
        if (holder.channelThumb == null || holder.comment == null) return;
        ChannelIconResolver.loadInto(holder.channelThumb, holder.comment.channelId, holder.comment.channelThumbnail, iconResolveListener);
    }

    /**
     * Reset image state for a view and cancel its pending loads so the
     * hosting activity can be collected after navigation.
     */
    public void resetImageView(View row) {
        if (row != null && row.getTag() instanceof ViewHolder) {
            ViewHolder holder = (ViewHolder) row.getTag();
            if (holder.imagesLoaded) {
                holder.imagesLoaded = false;
                if (holder.channelThumb != null) {
                    ImageLoader.unbindImageView(holder.channelThumb);
                }
            }
        }
    }

    private static class ViewHolder {
        TextView channelTitle;
        TextView content;
        TextView date;
        ImageView channelThumb;
        Comment comment;
        boolean imagesLoaded; // Tracks if we already triggered the load for this specific view
    }
}