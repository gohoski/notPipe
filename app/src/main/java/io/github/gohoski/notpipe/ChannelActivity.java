package io.github.gohoski.notpipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.KeyEvent;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.notpipe.api.ChannelApi;
import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.api.Metadata;
import io.github.gohoski.notpipe.api.ContentUnavailableException;
import io.github.gohoski.notpipe.data.Channel;
import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.ui.VideoAdapter;
import io.github.gohoski.notpipe.util.ImageLoader;

/**
 * Created by Gleb on 12.03.2026.
 */

public class ChannelActivity extends Activity {
    Metadata api;
    ChannelApi channelApi;
    String channelId;
    Channel channel;
    LinearLayout channelLayout;
    private PopupMenu tvMenu;
    Context context;
    ImageView banner;
    boolean isDestroyedFlag = false;

    TabHost tabHost;
    AbsListView forYouList;
    AbsListView videosList;
    VideoAdapter forYouAdapter;
    VideoAdapter videosAdapter;

    Spinner sortingSpinner;
    int currentSort = 0; // 0 = Latest, 1 = Popular
    boolean metadataGetVideosFailed = false;

    List<VideoInfo> forYouVideos = null;
    List<VideoInfo> latestVideos = null;
    List<VideoInfo> popularVideos = null;

    private void setAdapterForView(AbsListView view, VideoAdapter adapter) {
        if (view instanceof ListView) {
            ((ListView) view).setAdapter(adapter);
        } else if (view instanceof GridView) {
            ((GridView) view).setAdapter(adapter);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);

        channelId = getIntent().getStringExtra("ID");
        channelLayout = (LinearLayout) findViewById(R.id.channel);
        banner = (ImageView) findViewById(R.id.banner);
        context = this;

        channelLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (channel == null) return;

                TextView text = new TextView(context);
                text.setText(getString(R.string.subscribers, NumberFormat.getNumberInstance().format(channel.subscriberCount))
                        + "\n\n" + channel.description);
                text.setPadding(15,15,15,15);

                ScrollView scroll = new ScrollView(context);
                scroll.addView(text);

                new AlertDialog.Builder(context).setTitle(channel.title).setView(scroll).show();
            }
        });

        new LoadChannelTask().execute(channelId);
    }

    private void setupTabs(final boolean hasForYou, final boolean hasLatestFallback) {
        tabHost = (TabHost) findViewById(android.R.id.tabhost);
        tabHost.setup();

        if (hasForYou) {
            tabHost.addTab(tabHost.newTabSpec("for_you").setIndicator(getString(R.string.for_you)).setContent(R.id.for_you));

            forYouList = (AbsListView) findViewById(R.id.for_you_list);
            findViewById(R.id.for_you_loading).setVisibility(View.GONE);
            forYouList.setVisibility(View.VISIBLE);

            forYouAdapter = new VideoAdapter(context, R.layout.item_video_compact, forYouVideos, true);
            setAdapterForView(forYouList, forYouAdapter);

            forYouList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    ImageLoader.clearCache(); System.gc();
                    Intent intent = new Intent(ChannelActivity.this, VideoActivity.class);
                    intent.putExtra("ID", forYouVideos.get(position).id);
                    startActivity(intent);
                }
            });
        }

        tabHost.addTab(tabHost.newTabSpec("videos").setIndicator(getString(R.string.videos)).setContent(R.id.videos));

        sortingSpinner = (Spinner) findViewById(R.id.sorting_spinner);
        if (NotPipe.SDK < 11 && sortingSpinner != null) {
            sortingSpinner.setVisibility(View.GONE);
        } else {
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(context,
                    android.R.layout.simple_spinner_item,
                    new String[] {
                            context.getString(R.string.latest),
                            context.getString(R.string.popular)
                    });
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sortingSpinner.setAdapter(spinnerAdapter);
            sortingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (currentSort != position) {
                        currentSort = position;
                        new LoadVideosTask().execute(channelId);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                if ("videos".equals(tabId)) {
                    if (currentSort == 0 && hasLatestFallback && latestVideos != null) {
                        displayVideos(latestVideos);
                    } else {
                        if (currentSort == 0 && latestVideos != null) {
                            displayVideos(latestVideos);
                        } else if (currentSort == 1 && popularVideos != null) {
                            displayVideos(popularVideos);
                        } else {
                            new LoadVideosTask().execute(channelId);
                        }
                    }
                }
            }
        });

        if (!hasForYou) {
            tabHost.setCurrentTabByTag("videos");
            if (hasLatestFallback && latestVideos != null && currentSort == 0) {
                displayVideos(latestVideos);
            } else {
                new LoadVideosTask().execute(channelId);
            }
        }

        if (NotPipe.SDK < 11) {
            int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 33f, getResources().getDisplayMetrics());
            TabWidget widget = tabHost.getTabWidget();
            for (int i = 0; i < widget.getChildCount(); i++) {
                View child = widget.getChildAt(i);
                if (child != null && child.getLayoutParams() != null) {
                    child.getLayoutParams().height = height;
                }
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event){
        // TV Fallback: Force opening the overflow menu via key event because DPAD navigation gets stuck on UI views.
        if(event != null && event.getAction() == KeyEvent.ACTION_DOWN){
            int key = event.getKeyCode();
            if(key == KeyEvent.KEYCODE_INFO || key == KeyEvent.KEYCODE_MENU){
                showTvMenu();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private void showTvMenu() {
        View anchorView = getWindow().getDecorView();
        if (tvMenu == null) {
            tvMenu = new PopupMenu(this, anchorView);
            tvMenu.getMenuInflater().inflate(R.menu.menu_main, tvMenu.getMenu());
            tvMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    return onOptionsItemSelected(item);
                }
            });
        }
        tvMenu.show();
    }

    private void displayVideos(final List<VideoInfo> fetchedVideos) {
        findViewById(R.id.videos_loading).setVisibility(View.GONE);
        TextView noVideosView = (TextView) findViewById(R.id.no_videos);
        videosList = (AbsListView) findViewById(R.id.videos_list);
        videosList.setVisibility(View.VISIBLE);
        if (noVideosView != null) {
            if (fetchedVideos == null || fetchedVideos.isEmpty()) {
                noVideosView.setVisibility(View.VISIBLE);
            } else {
                noVideosView.setVisibility(View.GONE);
            }
        }
        if (fetchedVideos != null) {
            videosAdapter = new VideoAdapter(context, R.layout.item_video_compact, fetchedVideos, true);
            setAdapterForView(videosList, videosAdapter);
            videosList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    ImageLoader.clearCache(); System.gc();
                    Intent intent = new Intent(ChannelActivity.this, VideoActivity.class);
                    intent.putExtra("ID", fetchedVideos.get(position).id);
                    startActivity(intent);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (forYouAdapter != null) {
            forYouAdapter.notifyDataSetChanged();
        }
        if (videosAdapter != null) {
            videosAdapter.notifyDataSetChanged();
        }
        if (channel != null && banner != null) {
            ImageLoader.loadImage(channel.banner, banner, true);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        ImageLoader.clearCache();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (NotPipe.SDK < 11) {
            getMenuInflater().inflate(R.menu.menu_channel, menu);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (NotPipe.SDK < 11) {
            MenuItem latestItem = menu.findItem(R.id.action_sort_latest);
            MenuItem popularItem = menu.findItem(R.id.action_sort_popular);
            if (latestItem != null) {
                latestItem.setVisible(currentSort != 0);
            }
            if (popularItem != null) {
                popularItem.setVisible(currentSort != 1);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (NotPipe.SDK < 11) {
            if (item.getItemId() == R.id.action_sort_latest) {
                if (currentSort != 0) {
                    currentSort = 0;
                    new LoadVideosTask().execute(channelId);
                }
                return true;
            } else if (item.getItemId() == R.id.action_sort_popular) {
                if (currentSort != 1) {
                    currentSort = 1;
                    new LoadVideosTask().execute(channelId);
                }
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyedFlag = true;
        if (isFinishing()) {
            ImageLoader.clearCache();
        }
        if (banner != null) {
            banner.setImageDrawable(null);
        }
    }

    private class LoadChannelTask extends AsyncTask<String, Void, Channel> {
        private boolean contentUnavailable;

        @Override
        protected Channel doInBackground(String... strings) {
            try {
                if (isCancelled()) return null;
                api = Manager.getInstance().getMetadata();
                channelApi = Manager.getInstance().getChannelApi();
                return api.getChannel(strings[0]);
            } catch (ContentUnavailableException e) {
                e.printStackTrace();
                contentUnavailable = true;
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(final Channel fetched) {
            if (isCancelled() || isDestroyedFlag) return;
            if (fetched == null) {
                findViewById(R.id.loading).setVisibility(View.GONE);
                if (contentUnavailable) {
                    Toast.makeText(context, R.string.content_unavailable, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            channel = fetched;
            channelId = channel.id;

            ((TextView) findViewById(R.id.title)).setText(channel.title);
            ((TextView) findViewById(R.id.subscribers)).setText(getString(R.string.subscribers, Utils.formatNumber(context, channel.subscriberCount)));
            ((TextView) findViewById(R.id.description)).setText(channel.description);
            ImageLoader.loadImage(channel.thumbnail, ((ImageView) findViewById(R.id.thumbnail)), false);
            ImageLoader.loadImage(channel.banner, banner, false);

            // Immediately make the channel metadata and banner visible as requested
            channelLayout.setVisibility(View.VISIBLE);
            if (banner != null) {
                banner.setVisibility(View.VISIBLE);
            }

            if (channel.videos != null && !channel.videos.isEmpty()) {
                forYouVideos = channel.videos;
                findViewById(R.id.loading).setVisibility(View.GONE);
                setupTabs(true, false);
                if (tabHost != null) {
                    tabHost.setVisibility(View.VISIBLE);
                }
            } else {
                new LoadChannelVideosFallbackTask().execute(channelId, channel.title);
            }
        }
    }

    private class LoadChannelVideosFallbackTask extends AsyncTask<String, Void, Integer> {
        @Override
        protected void onPreExecute() {
            findViewById(R.id.loading).setVisibility(View.VISIBLE);
            // Kept visible so metadata and banner do not disappear when fetching videos
            channelLayout.setVisibility(View.VISIBLE);
            if (banner != null) {
                banner.setVisibility(View.VISIBLE);
            }
            if (tabHost != null) {
                tabHost.setVisibility(View.GONE);
            }
        }

        @Override
        protected Integer doInBackground(String... strings) {
            String id = strings[0];
            String title = strings[1];

            List<VideoInfo> videos = null;
            try {
                videos = api.getChannelVideos(id, 0);
            } catch (Exception e) {
                metadataGetVideosFailed = true;
            }

            if (videos != null && !videos.isEmpty()) {
                latestVideos = videos;
                metadataGetVideosFailed = false;
                return 0;
            }

            metadataGetVideosFailed = true;
            try {
                List<VideoInfo> searchResults = api.search(title);
                List<VideoInfo> filtered = new ArrayList<VideoInfo>();
                for (int i = 0; i < searchResults.size(); i++) {
                    VideoInfo video = searchResults.get(i);
                    if (id.equals(video.channelId)) {
                        filtered.add(video);
                    }
                }
                if (!filtered.isEmpty()) {
                    forYouVideos = filtered;
                    return 1;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return 2;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (isCancelled() || isDestroyedFlag) return;
            findViewById(R.id.loading).setVisibility(View.GONE);
            channelLayout.setVisibility(View.VISIBLE);

            if (result == 0) {
                setupTabs(false, true);
            } else if (result == 1) {
                setupTabs(true, false);
            } else {
                setupTabs(false, false);
            }

            if (tabHost != null) {
                tabHost.setVisibility(View.VISIBLE);
            }
        }
    }

    private class LoadVideosTask extends AsyncTask<String, Void, List<VideoInfo>> {
        @Override
        protected void onPreExecute() {
            findViewById(R.id.videos_loading).setVisibility(View.VISIBLE);
            AbsListView vList = (AbsListView) findViewById(R.id.videos_list);
            if (vList != null) {
                vList.setVisibility(View.GONE);
            }
            View noVideosView = findViewById(R.id.no_videos);
            if (noVideosView != null) {
                noVideosView.setVisibility(View.GONE);
            }
        }

        @Override
        protected List<VideoInfo> doInBackground(String... strings) {
            String id = strings[0];
            List<VideoInfo> result = null;

            if (!metadataGetVideosFailed) {
                try {
                    result = api.getChannelVideos(id, currentSort);
                } catch (Exception e) {
                    metadataGetVideosFailed = true;
                }
                if (result == null || result.isEmpty()) {
                    metadataGetVideosFailed = true;
                }
            }

            if (metadataGetVideosFailed) {
                try {
                    result = channelApi.getChannelVideos(id, currentSort);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(final List<VideoInfo> fetchedVideos) {
            if (isCancelled() || isDestroyedFlag) return;

            if (currentSort == 0) {
                latestVideos = fetchedVideos;
            } else {
                popularVideos = fetchedVideos;
            }

            displayVideos(fetchedVideos);
        }
    }
}
