package io.github.gohoski.notpipe.ui;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.notpipe.Utils;
import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.api.Metadata;

public class AutoCompleteAdapter extends BaseAdapter implements Filterable {
    private Context context;
    private Metadata api = Manager.getInstance().getMetadata();
    private Handler handler = new Handler();
    private List<String> suggestions = new ArrayList<String>();
    private boolean searchActive = false;
    private Runnable pendingRequest;
    private OnSuggestionsLoadedListener listener;

    private static final int DEBOUNCE_DELAY = 500;

    public interface OnSuggestionsLoadedListener {
        void onSuggestionsLoaded();
    }

    public void setOnSuggestionsLoadedListener(OnSuggestionsLoadedListener listener) {
        this.listener = listener;
    }

    public AutoCompleteAdapter(Context context) {
        this.context = context;
    }

    public void setSearchActive(boolean active) {
        searchActive = active;
        if (active) {
            cancelPending();
            suggestions.clear();
            notifyDataSetChanged();
        }
    }

    public void requestSuggestions(final String query) {
        cancelPending();
        if (searchActive || query == null || query.length() < 2) return;
        pendingRequest = new Runnable() {
            @Override
            public void run() {
                if (searchActive) return;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final List<String> results = api.searchSuggestions(query);
                            Log.d("AutoComplete", results.toString());
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!searchActive) {
                                        suggestions.clear();
                                        for (String r : results) suggestions.add(Utils.stripEmoji(r));
                                        notifyDataSetChanged();
                                        if (listener != null && !results.isEmpty()) {
                                            listener.onSuggestionsLoaded();
                                        }
                                    }
                                }
                            });
                        } catch (IOException e) {
                            Log.e("AutoComplete", "Network error", e);
                        }
                    }
                }).start();
            }
        };
        handler.postDelayed(pendingRequest, DEBOUNCE_DELAY);
    }

    private void cancelPending() {
        if (pendingRequest != null) {
            handler.removeCallbacks(pendingRequest);
            pendingRequest = null;
        }
    }

    @Override
    public int getCount() {
        return suggestions.size();
    }

    @Override
    public String getItem(int position) {
        return suggestions.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView textView;
        if (convertView == null) {
            textView = (TextView) LayoutInflater.from(context).inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
        } else {
            textView = (TextView) convertView;
        }
        textView.setText(getItem(position));
        return textView;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                return new FilterResults();
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
            }
        };
    }
}