package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.github.gohoski.notpipe.api.Auth;
import io.github.gohoski.notpipe.api.ChannelApi;
import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.api.Metadata;
import io.github.gohoski.notpipe.auth.AuthManager;
import io.github.gohoski.notpipe.config.Config;
import io.github.gohoski.notpipe.config.ConfigManager;
import io.github.gohoski.notpipe.data.Account;
import io.github.gohoski.notpipe.data.ChannelInfo;
import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.ui.AutoCompleteAdapter;
import io.github.gohoski.notpipe.ui.VideoAdapter;
import io.github.gohoski.notpipe.util.ChannelIconResolver;
import io.github.gohoski.notpipe.util.ImageLoader;
import io.github.gohoski.notpipe.util.InstancesUpdater;

public class MainActivity extends Activity implements InstancesUpdater.OnInstancesUpdatedListener {
    private static final String STATE_SEARCH_QUERY = "search_query";
    private static final String STATE_IS_SEARCH_MODE = "is_search_mode";
    private static final String STATE_IS_RECOMMENDATIONS = "is_recommendations";
    private static final int REQUEST_LOGIN = 1001;

    private VideoAdapter adapter;
    private List<VideoInfo> videos;
    private AutoCompleteTextView searchQuery;
    private AbsListView listView;
    private Context context;
    private Metadata metadata;
    private AutoCompleteAdapter autoCompleteAdapter;
    private Config config = ConfigManager.getInstance().getConfig();
    private boolean isSearchMode = false;
    private boolean isRecommendations = false;
    private boolean isDestroyedFlag = false;

    private TextView meStatus;
    private View meChannelRow;
    private ImageView meChannelIcon;
    private TextView meChannelTitle;
    private View meChannelLoading;
    private View meLoginBtn;
    private View meLogoutBtn;
    private TextView meHistoryTitle;
    private View meHistoryLoading;
    private TextView meHistoryEmpty;
    private View meHistoryScroll;
    private LinearLayout meHistoryStrip;

    private ChannelInfo authChannel;
    private boolean authChannelLoaded = false;
    private boolean authChannelLoading = false;
    private List<VideoInfo> historyVideos;
    private boolean historyLoaded = false;
    private boolean historyLoading = false;

    private VideoAdapter subscriptionsAdapter;
    private List<VideoInfo> subscriptionVideos;
    private ListView subscriptionsListView;
    private boolean subscriptionsLoaded = false;
    private boolean subscriptionsLoading = false;

    private View channelsHeader;
    private LinearLayout channelsStrip;
    private List<ChannelInfo> subscribedChannels;
    private boolean channelsLoaded = false;
    private String viewingChannelId = null;
    private List<VideoInfo> channelFeedVideos;
    private boolean channelVideosLoading = false;

    private List<ChannelTile> channelTiles = new ArrayList<ChannelTile>();
    private List<HistoryTile> historyTiles = new ArrayList<HistoryTile>();
    private static final int STRIP_CHECK_INTERVAL = 100;
    private Handler channelsStripHandler = new Handler();
    private Runnable channelsStripCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (isDestroyedFlag) return;
            checkVisibleStripChannels();
            checkVisibleHistoryItems();
            channelsStripHandler.postDelayed(this, STRIP_CHECK_INTERVAL);
        }
    };

    private static class RetainedState {
        List<VideoInfo> videos;
        boolean isSearchMode;
        boolean homeFeedIsRecommendations;
        String query;
        List<VideoInfo> subscriptionVideos;
        boolean subscriptionsLoaded;
        List<ChannelInfo> subscribedChannels;
        boolean channelsLoaded;
        String viewingChannelId;
        List<VideoInfo> channelFeedVideos;
        ChannelInfo authChannel;
        boolean authChannelLoaded;
        List<VideoInfo> historyVideos;
        boolean historyLoaded;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        RetainedState state = new RetainedState();
        state.videos = this.videos;
        state.isSearchMode = this.isSearchMode;
        state.homeFeedIsRecommendations = this.isRecommendations;
        if (searchQuery != null) {
            state.query = searchQuery.getText().toString();
        }
        state.subscriptionVideos = this.subscriptionVideos;
        state.subscriptionsLoaded = this.subscriptionsLoaded;
        state.subscribedChannels = this.subscribedChannels;
        state.channelsLoaded = this.channelsLoaded;
        state.viewingChannelId = this.viewingChannelId;
        state.channelFeedVideos = this.channelFeedVideos;
        state.authChannel = this.authChannel;
        state.authChannelLoaded = this.authChannelLoaded;
        state.historyVideos = this.historyVideos;
        state.historyLoaded = this.historyLoaded;
        return state;
    }

    private void setAdapterForView(AbsListView view, VideoAdapter adapter) {
        if (view instanceof ListView) {
            ((ListView) view).setAdapter(adapter);
        } else if (view instanceof GridView) {
            ((GridView) view).setAdapter(adapter);
        }
    }

    private void ensureHomeAdapter() {
        if (adapter == null && listView != null) {
            int layout;
            if (isSearchMode || isRecommendations) {
                layout = R.layout.item_video;
            } else {
                layout = R.layout.item_video_noicon;
            }
            adapter = new VideoAdapter(this, layout, new ArrayList<VideoInfo>());
            setAdapterForView(listView, adapter);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = (AbsListView) findViewById(R.id.videos);
        final ProgressBar loading = (ProgressBar) findViewById(R.id.loading);
        final LinearLayout noPopular = (LinearLayout) findViewById(R.id.no_popular);
        context = this;

        final ImageButton searchBtn;

        // ListView = phones, GridView = tablet
        if (listView instanceof ListView) {
            ListView lv = (ListView) listView;
            View headerView = getLayoutInflater().inflate(R.layout.header_search, lv, false);
            lv.addHeaderView(headerView, null, false);
            lv.setItemsCanFocus(true);
            searchQuery = (AutoCompleteTextView) headerView.findViewById(R.id.search_query);
            searchBtn = (ImageButton) headerView.findViewById(R.id.search_btn);
        } else {
            searchQuery = (AutoCompleteTextView) findViewById(R.id.search_query);
            searchBtn = (ImageButton) findViewById(R.id.search_btn);
        }
        ensureHomeAdapter();

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}
        });

        try {
            metadata = Manager.getInstance().getMetadata();
        } catch(IllegalStateException ignored) {}

        RetainedState retained = (RetainedState) getLastNonConfigurationInstance();
        if (retained != null) {
            videos = retained.videos;
            isSearchMode = retained.isSearchMode;
            isRecommendations = retained.homeFeedIsRecommendations;
            subscriptionVideos = retained.subscriptionVideos;
            subscriptionsLoaded = retained.subscriptionsLoaded;
            subscribedChannels = retained.subscribedChannels;
            channelsLoaded = retained.channelsLoaded;
            viewingChannelId = retained.viewingChannelId;
            channelFeedVideos = retained.channelFeedVideos;
            authChannel = retained.authChannel;
            authChannelLoaded = retained.authChannelLoaded;
            historyVideos = retained.historyVideos;
            historyLoaded = retained.historyLoaded;
        } else if (savedInstanceState != null) {
            isSearchMode = savedInstanceState.getBoolean(STATE_IS_SEARCH_MODE, false);
            isRecommendations = savedInstanceState.getBoolean(STATE_IS_RECOMMENDATIONS, false);
        }

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);
                if (item instanceof VideoInfo) {
                    Intent intent = new Intent(MainActivity.this, VideoActivity.class);
                    intent.putExtra("ID", ((VideoInfo) item).id);
                    startActivity(intent);
                }
            }
        });

        autoCompleteAdapter = new AutoCompleteAdapter(this);
        autoCompleteAdapter.setOnSuggestionsLoadedListener(new AutoCompleteAdapter.OnSuggestionsLoadedListener() {
            @Override
            public void onSuggestionsLoaded() {
                if (!isDestroyedFlag && searchQuery != null) {
                    searchQuery.showDropDown();
                }
            }
        });
        searchQuery.setAdapter(autoCompleteAdapter);
        searchQuery.setThreshold(3);

        if (retained != null && retained.query != null) {
            searchQuery.setText(retained.query);
        } else if (savedInstanceState != null) {
            String query = savedInstanceState.getString(STATE_SEARCH_QUERY);
            if (query != null) searchQuery.setText(query);
        }
        searchQuery.dismissDropDown();

        searchQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchQuery.hasFocus()) {
                    autoCompleteAdapter.setSearchActive(false);
                    autoCompleteAdapter.requestSuggestions(s.toString());
                }
            }
        });
        searchQuery.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String suggestion = (String) parent.getItemAtPosition(position);
                autoCompleteAdapter.setSearchActive(true);
                searchQuery.setText(suggestion);
                hideKeyboard();
                searchBtn.performClick();
            }
        });
        searchQuery.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                        (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    searchBtn.performClick();
                    return true;
                }
                return false;
            }
        });

        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String query = searchQuery.getText().toString().trim();
                if (query.length() != 0) {
                    String videoId = extractYouTubeId(query);
                    if (videoId != null) {
                        hideKeyboard();
                        Intent intent = new Intent(MainActivity.this, VideoActivity.class);
                        intent.putExtra("ID", videoId);
                        startActivity(intent);
                        return;
                    }
                    searchQuery.dismissDropDown();
                    autoCompleteAdapter.setSearchActive(true);
                    isSearchMode = true;
                    noPopular.setVisibility(View.GONE);
                    loading.setVisibility(View.VISIBLE);
                    View emptyView = findViewById(R.id.empty_videos);
                    if (emptyView != null) emptyView.setVisibility(View.GONE);
                    hideKeyboard();
                    new SearchTask().execute(query);
                }
            }
        });

        if (videos == null) {
            boolean isUpdatingInstances = false;
            if (config.isUpdateInstancesFromUrl()) {
                Calendar now = Calendar.getInstance();
                Calendar lastUpdate = Calendar.getInstance();
                now.setTimeInMillis(System.currentTimeMillis());
                lastUpdate.setTimeInMillis(config.getLastUpdate());
                now.set(Calendar.HOUR_OF_DAY, 0);
                now.set(Calendar.MINUTE, 0);
                now.set(Calendar.SECOND, 0);
                now.set(Calendar.MILLISECOND, 0);
                lastUpdate.set(Calendar.HOUR_OF_DAY, 0);
                lastUpdate.set(Calendar.MINUTE, 0);
                lastUpdate.set(Calendar.SECOND, 0);
                lastUpdate.set(Calendar.MILLISECOND, 0);
                if (Math.round((double) ((now.getTimeInMillis() - lastUpdate.getTimeInMillis()) / (24L * 60 * 60 * 1000))) >= config.getUpdateFrequency()) {
                    isUpdatingInstances = true;
                    new InstancesUpdater(this, this).updateInstances();
                }
            }

            if (!isUpdatingInstances) {
                if (isSearchMode && searchQuery.getText().toString().trim().length() > 0) {
                    loading.setVisibility(View.VISIBLE);
                    new SearchTask().execute(searchQuery.getText().toString().trim());
                } else {
                    isSearchMode = false;
                    new PopularTask().execute();
                }
            }
        } else {
            loading.setVisibility(View.GONE);
            noPopular.setVisibility(View.GONE);

            int layout;
            if (isSearchMode || isRecommendations) {
                layout = R.layout.item_video;
            } else {
                layout = R.layout.item_video_noicon;
            }

            adapter = new VideoAdapter(this, layout, videos);
            setAdapterForView(listView, adapter);
        }

        TabHost tabHost = (TabHost) findViewById(R.id.tabHost);
        if (tabHost != null) {
            tabHost.setup();

            TabHost.TabSpec tabSpec = tabHost.newTabSpec("home");
            tabSpec.setContent(R.id.home);
            safeSetIndicator(tabSpec, getString(R.string.tab_home), R.drawable.ic_home);
            tabHost.addTab(tabSpec);

            tabSpec = tabHost.newTabSpec("subscriptions");
            tabSpec.setContent(R.id.subscriptions);
            safeSetIndicator(tabSpec, getString(R.string.tab_subscriptions), R.drawable.ic_subscriptions);
            tabHost.addTab(tabSpec);

            tabSpec = tabHost.newTabSpec("you");
            tabSpec.setContent(R.id.me);
            safeSetIndicator(tabSpec, getString(R.string.tab_you), R.drawable.ic_user);
            tabHost.addTab(tabSpec);

            tabHost.setCurrentTab(0);

            TabWidget tabWidget = tabHost.getTabWidget();
            removeTabStrip(tabWidget);
            int compactHeight = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 42f, getResources().getDisplayMetrics());
            String[] titles = new String[] {
                    getString(R.string.tab_home),
                    getString(R.string.tab_subscriptions),
                    getString(R.string.tab_you)
            };
            int[] iconResIds = new int[] { R.drawable.ic_home, R.drawable.ic_subscriptions, R.drawable.ic_user };
            for (int i = 0; i < tabWidget.getChildCount(); i++) {
                View tabView = tabWidget.getChildAt(i);
                tabView.setBackgroundResource(R.drawable.tab_selector);
                tabView.setPadding(0, 0, 0, 0);
                if (tabView.getLayoutParams() != null) {
                    tabView.getLayoutParams().height = compactHeight;
                }
                if (tabView instanceof ViewGroup && tabView.findViewById(R.id.tab_icon) == null) {
                    ViewGroup vg = (ViewGroup) tabView;
                    vg.removeAllViews();
                    View customIndicator = createTabIndicator(titles[i], iconResIds[i]);
                    vg.addView(customIndicator, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.FILL_PARENT,
                            ViewGroup.LayoutParams.FILL_PARENT));
                }
            }
            tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
                @Override
                public void onTabChanged(String tabId) {
                    if ("subscriptions".equals(tabId)) {
                        loadSubscriptions();
                    } else if ("you".equals(tabId)) {
                        loadYouTabData();
                    }
                }
            });
        }

        setupSubscriptionsList();

        meStatus = (TextView) findViewById(R.id.me_status);
        meChannelRow = findViewById(R.id.me_channel_row);
        meChannelIcon = (ImageView) findViewById(R.id.me_channel_icon);
        meChannelTitle = (TextView) findViewById(R.id.me_channel_title);
        meChannelLoading = findViewById(R.id.me_channel_loading);
        meLoginBtn = findViewById(R.id.btn_me_login);
        meLogoutBtn = findViewById(R.id.btn_me_logout);
        meHistoryTitle = (TextView) findViewById(R.id.me_history_title);
        meHistoryLoading = findViewById(R.id.me_history_loading);
        meHistoryEmpty = (TextView) findViewById(R.id.me_history_empty);
        meHistoryScroll = findViewById(R.id.me_history_scroll);
        meHistoryStrip = (LinearLayout) findViewById(R.id.me_history_strip);

        if (meLoginBtn != null) {
            meLoginBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivityForResult(new Intent(MainActivity.this, LoginActivity.class), REQUEST_LOGIN);
                }
            });
        }
        if (meLogoutBtn != null) {
            meLogoutBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        AuthManager.getInstance().clearAccount();
                        Toast.makeText(MainActivity.this, R.string.logged_out, Toast.LENGTH_SHORT).show();
                        authChannel = null;
                        authChannelLoaded = false;
                        authChannelLoading = false;
                        historyVideos = null;
                        historyLoaded = false;
                        historyLoading = false;
                        updateYouTab();
                        subscriptionVideos = null;
                        subscriptionsLoaded = false;
                        updateSubscriptionsUI();
                        videos = null;
                        isSearchMode = false;
                        View loadingView = findViewById(R.id.loading);
                        if (loadingView != null) loadingView.setVisibility(View.VISIBLE);
                        new PopularTask().execute();
                    } catch (IllegalStateException e) {
                        Toast.makeText(MainActivity.this, R.string.logout_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        updateYouTab();
        if (historyLoaded && historyVideos != null) {
            populateHistoryStrip();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // detach bitmaps while MainActivity is in the background
        if (listView != null) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (adapter != null) {
                    adapter.resetImageView(child);
                }
            }
        }
    }

    private View createTabIndicator(String title, int iconResId) {
        View view = LayoutInflater.from(this).inflate(R.layout.tab_indicator, null);
        ImageView icon = (ImageView) view.findViewById(R.id.tab_icon);
        TextView text = (TextView) view.findViewById(R.id.tab_title);

        if (iconResId != 0) {
            icon.setImageResource(iconResId);
            icon.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.GONE);
        }

        text.setText(title);
        return view;
    }

    private void safeSetIndicator(TabHost.TabSpec tabSpec, String title, int iconResId) {
        try {
            // Reflection for Android 1.6+ (API 4+) custom view indicator
            View indicatorView = createTabIndicator(title, iconResId);
            java.lang.reflect.Method setIndicatorMethod = TabHost.TabSpec.class.getMethod("setIndicator", View.class);
            setIndicatorMethod.invoke(tabSpec, indicatorView);
        } catch (Exception e) {
            // Fallback for Android 1.5 (API 3)
            try {
                if (iconResId != 0) {
                    android.graphics.drawable.Drawable icon = getResources().getDrawable(iconResId);
                    tabSpec.setIndicator(title, icon);
                } else {
                    tabSpec.setIndicator(title);
                }
            } catch (Exception ex) {
                tabSpec.setIndicator(title);
            }
        }
    }

    private String extractYouTubeId(String query) {
        String url = query.trim();
        int startIndex;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            startIndex = url.indexOf("://") + 3;
        } else {
            startIndex = 0;
            url = "http://" + url;
        }
        String host = url.substring(startIndex);
        int slashIndex = host.indexOf("/");
        String domain = slashIndex >= 0 ? host.substring(0, slashIndex) : host;
        String path = slashIndex >= 0 ? host.substring(slashIndex) : "";
        if (domain.endsWith("youtube.com") && path.startsWith("/watch")) {
            int vIndex = path.indexOf("v=");
            if (vIndex >= 0) {
                int ampIndex = path.indexOf("&", vIndex);
                return ampIndex >= 0 ? path.substring(vIndex + 2, ampIndex) : path.substring(vIndex + 2);
            }
        }
        if (domain.endsWith("youtu.be") && path.length() > 1) {
            int slash = path.indexOf("/", 1);
            return slash >= 0 ? path.substring(1, slash) : path.substring(1);
        }
        return null;
    }

    @Override
    public void onInstancesUpdated() {
        if (isDestroyedFlag) return;
        if (videos == null) {
            if (isSearchMode && searchQuery.getText().toString().trim().length() > 0) {
                findViewById(R.id.no_popular).setVisibility(View.GONE);
                findViewById(R.id.loading).setVisibility(View.VISIBLE);
                new SearchTask().execute(searchQuery.getText().toString().trim());
            } else {
                isSearchMode = false;
                new PopularTask().execute();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (isSearchMode) {
                isSearchMode = false;
                searchQuery.setText("");
                findViewById(R.id.no_popular).setVisibility(View.GONE);
                findViewById(R.id.loading).setVisibility(View.VISIBLE);
                View emptyView = findViewById(R.id.empty_videos);
                if (emptyView != null) emptyView.setVisibility(View.GONE);
                Toast.makeText(this, R.string.confirm_exit, Toast.LENGTH_SHORT).show();
                new PopularTask().execute();
                return true;
            } else if (viewingChannelId != null) {
                // If viewing a single channel, Back brings you back to the full subscriptions feed
                showFullSubscriptionsFeed();
                return true;
            } else {
                finish();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && searchQuery != null) {
            imm.hideSoftInputFromWindow(searchQuery.getWindowToken(), 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (subscriptionsAdapter != null) {
            subscriptionsAdapter.notifyDataSetChanged();
        }
        updateYouTab();
        if (historyLoaded && historyVideos != null) {
            populateHistoryStrip();
        }
        updateSubscriptionsUI();
        channelsStripHandler.removeCallbacks(channelsStripCheckRunnable);
        channelsStripHandler.post(channelsStripCheckRunnable);
    }

    /**
     * Returns true if a user is currently authorized via AuthManager.
     */
    private boolean isLoggedIn() {
        try {
            return AuthManager.getInstance().isLoggedIn();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void setupSubscriptionsList() {
        channelsHeader = findViewById(R.id.channels_header);
        channelsStrip = (LinearLayout) findViewById(R.id.channels_strip);

        try {
            subscriptionsListView = (ListView) findViewById(R.id.subscriptions_list);
        } catch (ClassCastException e) {
            subscriptionsListView = null;
        }

        if (subscriptionsListView != null) {
            if (subscribedChannels != null && channelsLoaded) {
                populateChannelStrip();
            }
            if (subscriptionVideos != null && subscriptionsLoaded) {
                bindFeedAdapter();
            }
            subscriptionsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Object item = parent.getItemAtPosition(position);
                    if (item instanceof VideoInfo) {
                        Intent intent = new Intent(MainActivity.this, VideoActivity.class);
                        intent.putExtra("ID", ((VideoInfo) item).id);
                        startActivity(intent);
                    }
                }
            });
        }
        updateSubscriptionsUI();
    }

    /**
     * Returns the list of videos currently displayed in the subscriptions feed:
     * either the latest videos of a selected channel or the full subscriptions feed.
     */
    private List<VideoInfo> getDisplayedFeedVideos() {
        if (viewingChannelId != null) {
            return channelFeedVideos;
        }
        return subscriptionVideos;
    }

    /**
     * Rebuilds the subscriptions feed adapter from the currently displayed video list.
     */
    private void bindFeedAdapter() {
        List<VideoInfo> displayed = getDisplayedFeedVideos();
        if (displayed == null) return;        subscriptionsAdapter = new VideoAdapter(this, R.layout.item_video, displayed);
        if (subscriptionsListView != null) {
            subscriptionsListView.setAdapter(subscriptionsAdapter);
        }
    }

    /**
     * Removes Shorts (videos shorter than the configured threshold) from a feed.
     * Function is made mostly for yt2009 recommendations/subscriptions, which typically do not filter out shorts content.
     * Only applies when enabled; videos whose length is unknown are never removed
     * so real content is not accidentally hidden.
     */
    private List<VideoInfo> filterShorts(List<VideoInfo> feed, boolean enabled) {
        if (!enabled || feed == null) return feed;
        int threshold = config.getShortLengthSeconds();
        if (threshold <= 0) return feed;
        List<VideoInfo> filtered = new ArrayList<VideoInfo>();
        for (int i = 0; i < feed.size(); i++) {
            VideoInfo video = feed.get(i);
            int lengthSeconds = parseDurationSeconds(video.duration);
            if (lengthSeconds >= 0 && lengthSeconds < threshold) continue;
            filtered.add(video);
        }
        return filtered;
    }

    /**
     * Parses a duration string into a total number of seconds.
     * Accepts the formats produced by {@link io.github.gohoski.notpipe.Utils#formatDuration(int)}
     * ("M:SS" or "H:MM:SS"), as well as plain integer seconds.
     *
     * @param duration The duration string from a VideoInfo. May be null or empty
     *                 when the API did not provide a length.
     * @return The duration in seconds, or -1 if it could not be parsed.
     */
    private static int parseDurationSeconds(String duration) {
        if (duration == null) return -1;
        duration = duration.trim();
        if (duration.length() == 0) return -1;
        // convert 0:00 type string to int seconds
        // length int value to VideoInfo class is not added because not all APIs return
        // length int value; additionally we need seconds as a number only for this use-case
        String[] parts = duration.split(":");
        if (parts.length == 0) return -1;
        int total = 0;
        try {
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.length() == 0) return -1;
                int value = Integer.parseInt(part);
                int factor = 1;
                for (int j = i + 1; j < parts.length; j++) {
                    factor *= 60;
                }
                total += value * factor;
            }
            return total;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void populateChannelStrip() {
        if (channelsStrip == null || isDestroyedFlag) return;
        channelsStrip.removeAllViews();
        channelTiles.clear();
        if (subscribedChannels == null || subscribedChannels.isEmpty()) {
            channelsHeader.setVisibility(View.GONE);
            return;
        }
        channelsHeader.setVisibility(View.VISIBLE);
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < subscribedChannels.size(); i++) {
            final ChannelInfo channel = subscribedChannels.get(i);
            View row = inflater.inflate(R.layout.item_channel_strip, channelsStrip, false);
            TextView title = (TextView) row.findViewById(R.id.channel_title);
            ImageView thumb = (ImageView) row.findViewById(R.id.channel_thumb);
            title.setText(channel.title);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onChannelTileClicked(channel.id);
                }
            });
            channelsStrip.addView(row);
            channelTiles.add(new ChannelTile(channel, thumb, row));
        }
        updateChannelStripSelection();
        checkVisibleStripChannels();
    }

    private void onChannelTileClicked(String channelId) {
        if (channelVideosLoading) return;
        if (channelId.equals(viewingChannelId)) {
            // tapping again deselects and returns to full subscriptions feed
            showFullSubscriptionsFeed();
        } else {
            // Select channel
            openSubscribedChannel(channelId);
        }
    }

    private void updateChannelStripSelection() {
        for (int i = 0; i < channelTiles.size(); i++) {
            ChannelTile tile = channelTiles.get(i);
            boolean isSelected = viewingChannelId != null && viewingChannelId.equals(tile.channel.id);
            if (tile.thumb != null) {
                tile.thumb.setSelected(isSelected);
            }
            if (tile.rootView != null) {
                tile.rootView.setSelected(isSelected);
            }
        }
    }

    private void checkVisibleStripChannels() {
        if (channelsStrip == null || channelsStrip.getChildCount() == 0) return;
        if (channelsHeader == null || channelsHeader.getVisibility() != View.VISIBLE) return;
        if (subscriptionsListView != null && subscriptionsListView.getVisibility() != View.VISIBLE) return;
        if (!channelsStrip.isShown()) return;

        HorizontalScrollView scroll = (HorizontalScrollView) channelsStrip.getParent();
        if (scroll == null || scroll.getHeight() == 0 || scroll.getWidth() == 0) return;

        int visibleLeft = scroll.getScrollX();
        int visibleRight = visibleLeft + scroll.getWidth();

        for (int i = 0; i < channelsStrip.getChildCount(); i++) {
            View child = channelsStrip.getChildAt(i);
            if (child == null || child.getWidth() == 0) continue;

            int childLeft = 0;
            View curr = child;
            while (curr != null && curr != scroll) {
                childLeft += curr.getLeft();
                ViewParent currParent = curr.getParent();
                curr = (currParent instanceof View) ? (View) currParent : null;
            }
            if (curr != scroll) continue;
            int childRight = childLeft + child.getWidth();

            if (channelTiles.size() > i) {
                ChannelTile tile = channelTiles.get(i);
                if (tile == null) continue;

                boolean isVisible = (childRight > visibleLeft && childLeft < visibleRight);

                if (isVisible) {
                    if (!tile.iconRequested) {
                        tile.iconRequested = true;
                        ImageLoader.loadImage(tile.channel.thumbnail, tile.thumb);
                    }
                } else {
                    if (tile.iconRequested) {
                        tile.iconRequested = false;
                        if (tile.thumb != null) {
                            tile.thumb.setImageDrawable(null);
                        }
                    }
                }
            }
        }
    }

    private static class ChannelTile {
        ChannelInfo channel;
        ImageView thumb;
        View rootView;
        boolean iconRequested = false;

        ChannelTile(ChannelInfo channel, ImageView thumb, View rootView) {
            this.channel = channel;
            this.thumb = thumb;
            this.rootView = rootView;
        }
    }

    private void openSubscribedChannel(String channelId) {
        if (channelId == null || channelId.length() == 0 || channelVideosLoading) return;
        viewingChannelId = channelId;
        channelFeedVideos = null;
        channelVideosLoading = true;
        updateChannelStripSelection();
        View subLoading = findViewById(R.id.subscriptions_loading);
        if (subLoading != null) subLoading.setVisibility(View.VISIBLE);
        View subEmpty = findViewById(R.id.subscriptions_empty);
        if (subEmpty != null) subEmpty.setVisibility(View.GONE);
        updateSubscriptionsUI();
        ChannelInfo info = findSubscribedChannel(channelId);
        if (info != null && info.thumbnail != null && info.thumbnail.length() > 0) {
            ChannelIconResolver.setResolved(channelId, info.thumbnail);
        }
        new ChannelVideosTask().execute(channelId, info);
    }

    /**
     * Finds a subscribed channel's cached info by its ID, or null if not found.
     */
    private ChannelInfo findSubscribedChannel(String channelId) {
        if (subscribedChannels == null) return null;
        for (int i = 0; i < subscribedChannels.size(); i++) {
            ChannelInfo channel = subscribedChannels.get(i);
            if (channel != null && channel.id != null && channel.id.equals(channelId)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * Returns from a channel video list back to the full subscriptions feed.
     */
    private void showFullSubscriptionsFeed() {
        viewingChannelId = null;
        channelFeedVideos = null;
        updateChannelStripSelection();
        bindFeedAdapter();
        updateSubscriptionsUI();
    }

    /**
     * Refreshes subscriptions tab visibility from login state and cached data.
     */
    private void updateSubscriptionsUI() {
        boolean loggedIn = isLoggedIn();
        View subHint = findViewById(R.id.subscriptions_hint);
        View subLoading = findViewById(R.id.subscriptions_loading);
        View subEmpty = findViewById(R.id.subscriptions_empty);
        if (subHint != null)
            subHint.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        if (!loggedIn) {
            if (channelsHeader != null) channelsHeader.setVisibility(View.GONE);
            if (subLoading != null) subLoading.setVisibility(View.GONE);
            if (subEmpty != null) subEmpty.setVisibility(View.GONE);
            if (subscriptionsListView != null) subscriptionsListView.setVisibility(View.GONE);
            return;
        }
        boolean hasChannels = channelsLoaded && subscribedChannels != null && !subscribedChannels.isEmpty();
        if (channelsHeader != null)
            channelsHeader.setVisibility(hasChannels ? View.VISIBLE : View.GONE);
        boolean isLoading = subscriptionsLoading || channelVideosLoading;
        if (subLoading != null)
            subLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        List<VideoInfo> displayed = getDisplayedFeedVideos();
        boolean hasVideos = displayed != null && !displayed.isEmpty();
        if (subscriptionsListView != null)
            subscriptionsListView.setVisibility(hasVideos && !channelVideosLoading ? View.VISIBLE : View.GONE);
        boolean showEmpty = subscriptionsLoaded && !isLoading && !hasVideos && viewingChannelId == null;
        if (subEmpty != null)
            subEmpty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
    }

    /**
     * Loads the subscriptions feed on demand, i.e. only when the user opens
     * the Subscriptions tab. Shows cached data when already loaded and ignores
     * calls while a load is in progress, so startup never triggers a request.
     */
    private void loadSubscriptions() {
        if (!isLoggedIn()) {
            updateSubscriptionsUI();
            return;
        }
        if (subscriptionsLoaded && subscriptionVideos != null) {
            updateSubscriptionsUI();
            return;
        }
        if (subscriptionsLoading) {
            return;
        }
        if (subscriptionsListView == null) {
            try {
                subscriptionsListView = (ListView) findViewById(R.id.subscriptions_list);
            } catch (ClassCastException e) {
                subscriptionsListView = null;
            }
            if (subscriptionsListView == null) return;
        }
        subscriptionsLoading = true;
        View subLoading = findViewById(R.id.subscriptions_loading);
        if (subLoading != null) subLoading.setVisibility(View.VISIBLE);
        View subEmpty = findViewById(R.id.subscriptions_empty);
        if (subEmpty != null) subEmpty.setVisibility(View.GONE);
        new SubscriptionsTask().execute();
    }

    private void updateYouTab() {
        boolean loggedIn = isLoggedIn();
        if (meStatus != null) {
            meStatus.setText(loggedIn ? R.string.logged_in : R.string.not_logged_in);
            meStatus.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        }
        if (meLoginBtn != null) meLoginBtn.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        if (meChannelRow != null) meChannelRow.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        if (loggedIn) {
            // While the account is loading show a ProgressBar instead of plain
            // "Loading" text, keeping the row (and the Logout button on the
            // right) visible so the user can log out even if it never loads.
            // Once loaded, the ProgressBar below takes over for history videos.
            boolean channelLoading = authChannelLoading;
            if (meChannelLoading != null) {
                meChannelLoading.setVisibility(channelLoading ? View.VISIBLE : View.GONE);
            }
            if (meChannelTitle != null) {
                meChannelTitle.setVisibility(channelLoading ? View.GONE : View.VISIBLE);
                if (!channelLoading) {
                    if (authChannel != null && authChannel.title != null && authChannel.title.length() > 0) {
                        meChannelTitle.setText(authChannel.title);
                    } else {
                        meChannelTitle.setText("");
                    }
                }
            }
            if (meChannelIcon != null) {
                if (channelLoading) {
                    meChannelIcon.setVisibility(View.GONE);
                    meChannelIcon.setImageDrawable(null);
                } else if (authChannel != null && authChannel.thumbnail != null && authChannel.thumbnail.length() > 0) {
                    meChannelIcon.setVisibility(View.VISIBLE);
                    ImageLoader.loadImage(authChannel.thumbnail, meChannelIcon);
                } else {
                    meChannelIcon.setVisibility(View.GONE);
                    meChannelIcon.setImageDrawable(null);
                }
            }
        } else {
            if (meChannelIcon != null) meChannelIcon.setImageDrawable(null);
            if (meChannelTitle != null) meChannelTitle.setText("");
            if (meChannelLoading != null) meChannelLoading.setVisibility(View.GONE);
            if (meHistoryTitle != null) meHistoryTitle.setVisibility(View.GONE);
            if (meHistoryLoading != null) meHistoryLoading.setVisibility(View.GONE);
            if (meHistoryEmpty != null) meHistoryEmpty.setVisibility(View.GONE);
            if (meHistoryScroll != null) meHistoryScroll.setVisibility(View.GONE);
        }
        updateHistoryUI();

        updateSubscriptionsUI();
        if (!loggedIn) {
            authChannel = null;
            authChannelLoaded = false;
            authChannelLoading = false;
            historyVideos = null;
            historyLoaded = false;
            historyLoading = false;
            historyTiles.clear();
            if (meHistoryStrip != null) meHistoryStrip.removeAllViews();
            subscriptionVideos = null;
            subscriptionsLoaded = false;
            subscriptionsLoading = false;
            subscribedChannels = null;
            channelsLoaded = false;
            viewingChannelId = null;
            channelFeedVideos = null;
            channelVideosLoading = false;
            updateHistoryUI();
            updateSubscriptionsUI();
        }
    }

    /**
     * Loads the auth channel and watch history on demand, i.e. only when logged in.
     * Cached data is shown immediately, concurrent loads are ignored.
     * Channel loads first; history starts only after the channel has loaded
     * so just one spinner shows at a time (channel row, then history).
     */
    private void loadYouTabData() {
        if (!isLoggedIn()) {
            updateYouTab();
            return;
        }
        updateYouTab();
        boolean needChannel = !authChannelLoaded && !authChannelLoading;
        boolean needHistory = !historyLoaded && !historyLoading;
        if (!needChannel && !needHistory) {
            updateHistoryUI();
            return;
        }
        Account account;
        Auth auth;
        try {
            account = AuthManager.getInstance().getAccount();
            auth = AuthManager.getInstance().getAuth();
        } catch (IllegalStateException e) {
            return;
        }
        if (account == null || auth == null) return;
        if (needChannel) {
            authChannelLoading = true;
            updateYouTab();
            updateHistoryUI();
            new AuthChannelTask().execute();
            // History is chained from AuthChannelTask.onPostExecute.
        } else if (needHistory) {
            startHistoryLoad();
        }
    }

    /**
     * Starts the history load. Called either directly when the channel is
     * already cached, or chained after the channel finishes loading.
     */
    private void startHistoryLoad() {
        if (historyLoaded || historyLoading) return;
        if (!isLoggedIn()) return;
        try {
            if (AuthManager.getInstance().getAccount() == null
                    || AuthManager.getInstance().getAuth() == null) {
                return;
            }
        } catch (IllegalStateException e) {
            return;
        }
        historyLoading = true;
        updateHistoryUI();
        new HistoryTask().execute();
    }

    /**
     * Refreshes history section visibility from login state and cached data.
     * Hidden entirely while the account itself is still loading so only the
     * channel-row spinner shows first; the history spinner follows afterwards.
     */
    private void updateHistoryUI() {
        if (meHistoryTitle == null) return;
        if (!isLoggedIn()) return;
        if (authChannelLoading && !authChannelLoaded) {
            if (meHistoryLoading != null) meHistoryLoading.setVisibility(View.GONE);
            meHistoryTitle.setVisibility(View.GONE);
            if (meHistoryEmpty != null) meHistoryEmpty.setVisibility(View.GONE);
            if (meHistoryScroll != null) meHistoryScroll.setVisibility(View.GONE);
            return;
        }
        boolean loading = historyLoading && (historyVideos == null || historyVideos.isEmpty());
        if (meHistoryLoading != null) meHistoryLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (!historyLoaded && !historyLoading) {
            meHistoryTitle.setVisibility(View.GONE);
            if (meHistoryEmpty != null) meHistoryEmpty.setVisibility(View.GONE);
            if (meHistoryScroll != null) meHistoryScroll.setVisibility(View.GONE);
            return;
        }
        boolean hasVideos = historyVideos != null && !historyVideos.isEmpty();
        meHistoryTitle.setVisibility(View.VISIBLE);
        if (meHistoryScroll != null) meHistoryScroll.setVisibility(hasVideos ? View.VISIBLE : View.GONE);
        if (meHistoryEmpty != null) meHistoryEmpty.setVisibility(!loading && !hasVideos ? View.VISIBLE : View.GONE);
    }

    /**
     * Rebuilds the horizontal history strip from cached history videos.
     * Uses the dedicated small item_history layout (160dip fixed width,
     * thumbnail is 16:9 so ~90dip tall). Thumbnails are NOT loaded here;
     * checkVisibleHistoryItems() loads only the on-screen ones, same as
     * the channel strip.
     */
    private void populateHistoryStrip() {
        if (meHistoryStrip == null || isDestroyedFlag) return;
        meHistoryStrip.removeAllViews();
        historyTiles.clear();
        if (historyVideos == null || historyVideos.isEmpty()) {
            updateHistoryUI();
            return;
        }
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < historyVideos.size(); i++) {
            final VideoInfo video = historyVideos.get(i);
            if (video == null) continue;
            View row = inflater.inflate(R.layout.item_history, meHistoryStrip, false);
            TextView title = (TextView) row.findViewById(R.id.video_title);
            TextView channel = (TextView) row.findViewById(R.id.channel_title);
            ImageView thumb = (ImageView) row.findViewById(R.id.video_thumbnail);
            TextView duration = (TextView) row.findViewById(R.id.duration);
            if (title != null) title.setText(video.title);
            if (channel != null) {
                String views = video.views > 0 ? getString(R.string.views, Utils.formatNumber(MainActivity.this, video.views)) : null;
                if (video.channel != null && video.channel.length() > 0) {
                    if (views != null) {
                        channel.setText(video.channel + " · " + views);
                    } else {
                        channel.setText(video.channel);
                    }
                    channel.setVisibility(View.VISIBLE);
                } else if (views != null) {
                    channel.setText(views);
                    channel.setVisibility(View.VISIBLE);
                } else {
                    channel.setVisibility(View.GONE);
                }
            }
            if (duration != null) {
                if (video.duration != null && video.duration.length() > 0) {
                    duration.setText(video.duration);
                    duration.setVisibility(View.VISIBLE);
                } else {
                    duration.setVisibility(View.GONE);
                }
            }
            if (thumb != null) {
                thumb.setImageDrawable(null);
            }
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, VideoActivity.class);
                    intent.putExtra("ID", video.id);
                    startActivity(intent);
                }
            });
            meHistoryStrip.addView(row);
            historyTiles.add(new HistoryTile(video, thumb));
        }
        updateHistoryUI();
        checkVisibleHistoryItems();
    }

    private static class HistoryTile {
        VideoInfo video;
        ImageView thumb;
        boolean thumbRequested = false;

        HistoryTile(VideoInfo video, ImageView thumb) {
            this.video = video;
            this.thumb = thumb;
        }
    }

    /**
     * Loads thumbnails only for history cards currently visible on screen,
     * unloading off-screen ones to free RAM. Mirrors checkVisibleStripChannels().
     * Polled every STRIP_CHECK_INTERVAL from channelsStripCheckRunnable.
     */
    private void checkVisibleHistoryItems() {
        if (meHistoryStrip == null || meHistoryStrip.getChildCount() == 0) return;
        if (meHistoryScroll == null || meHistoryScroll.getVisibility() != View.VISIBLE) return;
        if (!meHistoryStrip.isShown()) return;

        HorizontalScrollView scroll = (HorizontalScrollView) meHistoryStrip.getParent();
        if (scroll == null || scroll.getHeight() == 0 || scroll.getWidth() == 0) return;

        int visibleLeft = scroll.getScrollX();
        int visibleRight = visibleLeft + scroll.getWidth();

        for (int i = 0; i < meHistoryStrip.getChildCount(); i++) {
            View child = meHistoryStrip.getChildAt(i);
            if (child == null || child.getWidth() == 0) continue;

            int childLeft = 0;
            View curr = child;
            while (curr != null && curr != scroll) {
                childLeft += curr.getLeft();
                ViewParent currParent = curr.getParent();
                curr = (currParent instanceof View) ? (View) currParent : null;
            }
            if (curr != scroll) continue;
            int childRight = childLeft + child.getWidth();

            if (historyTiles.size() > i) {
                HistoryTile tile = historyTiles.get(i);
                if (tile == null) continue;

                boolean isVisible = (childRight > visibleLeft && childLeft < visibleRight);

                if (isVisible) {
                    if (!tile.thumbRequested) {
                        tile.thumbRequested = true;
                        ImageLoader.loadImage(tile.video.thumbnail, tile.thumb);
                    }
                } else {
                    if (tile.thumbRequested) {
                        tile.thumbRequested = false;
                        if (tile.thumb != null) {
                            tile.thumb.setImageDrawable(null);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (searchQuery != null) {
            outState.putString(STATE_SEARCH_QUERY, searchQuery.getText().toString());
        }
        outState.putBoolean(STATE_IS_SEARCH_MODE, isSearchMode);
        outState.putBoolean(STATE_IS_RECOMMENDATIONS, isRecommendations);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        if (adapter != null) {
            try {
                super.onRestoreInstanceState(savedInstanceState);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        ImageLoader.clearCache();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyedFlag = true;
        channelsStripHandler.removeCallbacks(channelsStripCheckRunnable);
        if (isFinishing()) {
            ImageLoader.clearCache();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOGIN && resultCode == RESULT_OK) {
            authChannel = null;
            authChannelLoaded = false;
            authChannelLoading = false;
            historyVideos = null;
            historyLoaded = false;
            historyLoading = false;
            historyTiles.clear();
            if (meHistoryStrip != null) meHistoryStrip.removeAllViews();
            updateYouTab();
            // Refresh feed to show recommendations after successful login
            videos = null;
            isSearchMode = false;
            subscriptionVideos = null;
            subscriptionsLoaded = false;
            subscriptionsLoading = false;
            updateSubscriptionsUI();
            View loadingView = findViewById(R.id.loading);
            if (loadingView != null) loadingView.setVisibility(View.VISIBLE);
            View noPopularView = findViewById(R.id.no_popular);
            if (noPopularView != null) noPopularView.setVisibility(View.GONE);
            // Switch to Home tab to show new feed
            try {
                TabHost tabHost = (TabHost) findViewById(R.id.tabHost);
                if (tabHost != null) tabHost.setCurrentTab(0);
            } catch (Exception ignored) {}
            new PopularTask().execute();
        }
    }

    /**
     * Removes the bottom strip lines underneath TabWidget across all Android versions.
     */
    private void removeTabStrip(TabWidget tabWidget) {
        if (tabWidget == null) return;

        // Android 1.6+ (API Level 4+)
        try {
            java.lang.reflect.Method setStripEnabled = tabWidget.getClass().getMethod("setStripEnabled", boolean.class);
            setStripEnabled.invoke(tabWidget, false);
        } catch (Exception ignored) {}

        // Android 1.5 (API Level 3) fallback: disable internal drawing flag
        try {
            java.lang.reflect.Field drawStripsField = tabWidget.getClass().getDeclaredField("mDrawBottomStrips");
            drawStripsField.setAccessible(true);
            drawStripsField.setBoolean(tabWidget, false);
        } catch (Exception ignored) {}

        // Replace strip drawables with a transparent ColorDrawable
        String[] stripFields = new String[] { "mBottomLeftStrip", "mBottomRightStrip", "mLeftStrip", "mRightStrip" };
        for (int i = 0; i < stripFields.length; i++) {
            try {
                java.lang.reflect.Field field = tabWidget.getClass().getDeclaredField(stripFields[i]);
                field.setAccessible(true);
                field.set(tabWidget, new android.graphics.drawable.ColorDrawable(0));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return true;
        } else if (item.getItemId() == R.id.menu_about) {
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(15, 15, 15, 15);

            final TextView app = new TextView(this);
            app.setText(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
            app.setTypeface(null, Typeface.BOLD);
            app.setTextSize(20);
            app.setGravity(Gravity.CENTER_HORIZONTAL);
            layout.addView(app);

            final ImageView imageView = new ImageView(this);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            imageView.setImageResource(R.drawable.main_icon);
            layout.addView(imageView);

            final TextView text = new TextView(this);
            text.setText(getString(R.string.about_) + "\n\nMIT License\n" +
                    "\n" +
                    "Copyright (c) 2021-2025 Arman Jussupgaliyev\n" +
                    "\n" +
                    "Permission is hereby granted, free of charge, to any person obtaining a copy\n" +
                    "of this software and associated documentation files (the \"Software\"), to deal\n" +
                    "in the Software without restriction, including without limitation the rights\n" +
                    "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n" +
                    "copies of the Software, and to permit persons to whom the Software is\n" +
                    "furnished to do so, subject to the following conditions:\n" +
                    "\n" +
                    "The above copyright notice and this permission notice shall be included in all\n" +
                    "copies or substantial portions of the Software.\n" +
                    "\n" +
                    "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n" +
                    "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n" +
                    "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n" +
                    "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n" +
                    "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n" +
                    "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n" +
                    "SOFTWARE.");
            layout.addView(text);

            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(layout);

            new android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setView(scrollView)
                    .setNeutralButton(android.R.string.ok, null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class SearchTask extends AsyncTask<String, Void, SearchResult> {
        @Override
        protected SearchResult doInBackground(String... params) {
            SearchResult result = new SearchResult();
            try {
                if (metadata != null) {
                    result.videos = metadata.search(params[0]);
                } else {
                    result.error = new Exception("Metadata client not initialized.");
                }
            } catch (Exception e) {
                result.error = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(SearchResult result) {
            if (isDestroyedFlag) return;
            View loadingView = findViewById(R.id.loading);
            if (loadingView != null) loadingView.setVisibility(View.GONE);
            TextView emptyView = (TextView) findViewById(R.id.empty_videos);
            if (result.error != null) {
                Toast.makeText(MainActivity.this, getString(R.string.search_failed, result.error.getMessage()), Toast.LENGTH_LONG).show();
                if (emptyView != null) emptyView.setVisibility(View.GONE);
            } else if (result.videos != null) {
                videos = result.videos;
                adapter = new VideoAdapter(MainActivity.this, R.layout.item_video, videos);
                if (listView != null) {
                    setAdapterForView(listView, adapter);
                }
                if (emptyView != null) {
                    if (videos == null || videos.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                    } else {
                        emptyView.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    private class SearchResult {
        List<VideoInfo> videos;
        Exception error;
    }

    private class PopularTask extends AsyncTask<Void, Void, PopularResult> {
        @Override
        protected PopularResult doInBackground(Void... params) {
            PopularResult result = new PopularResult();
            try {
                try {
                    if (AuthManager.getInstance().isLoggedIn()) {
                        Account account = AuthManager.getInstance().getAccount();
                        Auth auth = AuthManager.getInstance().getAuth();
                        if (account != null && auth != null) {
                            try {
                                List<VideoInfo> rec = auth.getRecommendations(account);
                                if (rec != null && !rec.isEmpty()) {
                                    result.videos = rec;
                                    result.isRecommendations = true;
                                    return result;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (IllegalStateException ignored) {}

                Metadata popularApi = null;
                try {
                    popularApi = Manager.getInstance().getPopularMetadata();
                } catch (Exception ignored) {}
                if (popularApi != null) {
                    try {
                        result.videos = popularApi.getPopularVideos();
                        return result;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (metadata != null) {
                    result.videos = metadata.getPopularVideos();
                } else {
                    result.error = new Exception("Popular unavailable");
                }
            } catch (Exception e) {
                e.printStackTrace();
                result.error = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(PopularResult result) {
            if (isDestroyedFlag) return;
            View loadingView = findViewById(R.id.loading);
            if (loadingView != null) loadingView.setVisibility(View.GONE);
            View noPopularView = findViewById(R.id.no_popular);
            TextView emptyView = (TextView) findViewById(R.id.empty_videos);
            if (emptyView != null) emptyView.setVisibility(View.GONE);
            if (result.error != null) {
                if (noPopularView != null) noPopularView.setVisibility(View.VISIBLE);
                result.error.printStackTrace();
            } else {
                result.videos = filterShorts(result.videos, config.isHideShortsRecommendations());
                if (metadata == null || result.videos == null || result.videos.size() == 0) {
                    if (noPopularView != null) noPopularView.setVisibility(View.VISIBLE);
                } else {
                    if (noPopularView != null) noPopularView.setVisibility(View.GONE);
                    videos = result.videos;
                    isRecommendations = result.isRecommendations;
                    int layout = result.isRecommendations ? R.layout.item_video : R.layout.item_video_noicon;
                    adapter = new VideoAdapter(context, layout, videos);
                    if (listView != null) {
                        setAdapterForView(listView, adapter);
                    }
                }
            }
        }
    }

    private class PopularResult {
        List<VideoInfo> videos;
        boolean isRecommendations;
        Exception error;
    }

    private class SubscriptionsTask extends AsyncTask<Void, Void, SubscriptionsResult> {
        @Override
        protected SubscriptionsResult doInBackground(Void... params) {
            SubscriptionsResult result = new SubscriptionsResult();
            try {
                Account account = AuthManager.getInstance().getAccount();
                Auth auth = AuthManager.getInstance().getAuth();
                if (account == null || auth == null) {
                    result.error = new Exception("Not logged in");
                } else {
                    try {
                        result.channels = auth.getSubscribedChannels(account);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // Show the channel list immediately while the video feed
                    // is still loading for gradual loading without sudden hides.
                    if (result.channels != null) {
                        final List<ChannelInfo> earlyChannels = result.channels;
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isDestroyedFlag || !isLoggedIn()) return;
                                subscribedChannels = earlyChannels;
                                channelsLoaded = true;
                                populateChannelStrip();
                                updateSubscriptionsUI();
                            }
                        });
                    }
                    result.videos = auth.getSubscriptionsFeed(account);
                }
            } catch (Exception e) {
                e.printStackTrace();
                result.error = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(SubscriptionsResult result) {
            subscriptionsLoading = false;
            if (isDestroyedFlag) return;
            View loadingView = findViewById(R.id.subscriptions_loading);
            if (loadingView != null) loadingView.setVisibility(View.GONE);
            if (result.channels != null) {
                subscribedChannels = result.channels;
                channelsLoaded = true;
                populateChannelStrip();
            }
            if (!isLoggedIn()) {
                updateSubscriptionsUI();
                return;
            }
            if (result.error != null) {
                Toast.makeText(MainActivity.this, getString(R.string.subscriptions_failed, result.error.getMessage()), Toast.LENGTH_LONG).show();
                updateSubscriptionsUI();
            } else if (result.videos != null) {
                subscriptionVideos = filterShorts(result.videos, config.isHideShortsSubscriptions());
                subscriptionsLoaded = true;
                bindFeedAdapter();
                updateSubscriptionsUI();
            } else {
                updateSubscriptionsUI();
            }
        }
    }

    private class SubscriptionsResult {
        List<VideoInfo> videos;
        List<ChannelInfo> channels;
        Exception error;
    }

    private class ChannelVideosTask extends AsyncTask<Object, Void, ChannelVideosResult> {
        @Override
        protected ChannelVideosResult doInBackground(Object... params) {
            ChannelVideosResult result = new ChannelVideosResult();
            result.channelId = (String) params[0];
            // Channel video endpoints don't return channel metadata, so reuse
            // the info (name and icon) already loaded for the strip tile.
            ChannelInfo info = params.length > 1 ? (ChannelInfo) params[1] : null;
            try {
                ChannelApi channelApi = Manager.getInstance().getChannelApi();
                result.videos = channelApi.getChannelVideos(result.channelId, 0);
            } catch (Exception e) {
                e.printStackTrace();
                result.error = e;
            }
            if (result.videos != null && info != null) {
                applyChannelMetadata(result.videos, info);
            }
            return result;
        }

        @Override
        protected void onPostExecute(ChannelVideosResult result) {
            channelVideosLoading = false;
            if (isDestroyedFlag) return;
            if (result.channelId == null || !result.channelId.equals(viewingChannelId)) return;
            View subLoading = findViewById(R.id.subscriptions_loading);
            if (subLoading != null) subLoading.setVisibility(View.GONE);
            if (result.error != null) {
                Toast.makeText(MainActivity.this, getString(R.string.channel_videos_failed, result.error.getMessage()), Toast.LENGTH_LONG).show();
                viewingChannelId = null;
                channelFeedVideos = null;
                updateChannelStripSelection();
                updateSubscriptionsUI();
                return;
            }
            channelFeedVideos = result.videos != null ? result.videos : new ArrayList<VideoInfo>();
            bindFeedAdapter();
            updateSubscriptionsUI();
        }
    }

    /**
     * Fills in channel name, icon URL and channel ID on videos returned by
     * channel endpoints (which don't include this metadata), reusing the info
     * already fetched with the subscribed channels list. The icon URL matches
     * the strip tile's, so ImageLoader serves it straight from the memory cache
     * instead of downloading it again for every video row.
     */
    private static void applyChannelMetadata(List<VideoInfo> videos, ChannelInfo info) {
        boolean hasTitle = info.title != null && info.title.length() > 0;
        boolean hasIcon = info.thumbnail != null && info.thumbnail.length() > 0;
        if (!hasTitle && !hasIcon) return;
        for (int i = 0; i < videos.size(); i++) {
            VideoInfo video = videos.get(i);
            boolean needsTitle = hasTitle && (video.channel == null || video.channel.length() == 0);
            boolean needsIcon = hasIcon && (video.channelThumbnail == null || video.channelThumbnail.length() == 0);
            if (!needsTitle && !needsIcon && video.channelId != null) continue;
            videos.set(i, new VideoInfo(
                    video.id, video.title, video.thumbnail,
                    needsTitle ? info.title : video.channel,
                    needsIcon ? info.thumbnail : video.channelThumbnail,
                    video.channelId != null ? video.channelId : info.id,
                    video.duration, video.views, video.publishedAt));
        }
    }

    private class ChannelVideosResult {
        String channelId;
        List<VideoInfo> videos;
        Exception error;
    }

    private class AuthChannelTask extends AsyncTask<Void, Void, AuthChannelResult> {
        @Override
        protected AuthChannelResult doInBackground(Void... params) {
            AuthChannelResult result = new AuthChannelResult();
            try {
                Account account = AuthManager.getInstance().getAccount();
                Auth auth = AuthManager.getInstance().getAuth();
                if (account == null || auth == null) {
                    result.error = new Exception("Not logged in");
                } else {
                    result.channel = auth.getAuthChannel(account);
                }
            } catch (Exception e) {
                e.printStackTrace();
                result.error = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(AuthChannelResult result) {
            authChannelLoading = false;
            if (isDestroyedFlag) return;
            if (!isLoggedIn()) return;
            if (result.error != null) {
                authChannelLoaded = true;
                updateYouTab();
            } else {
                authChannel = result.channel;
                authChannelLoaded = true;
                updateYouTab();
            }
            // Chain history after the channel so spinners appear sequentially.
            startHistoryLoad();
        }
    }

    private class AuthChannelResult {
        ChannelInfo channel;
        Exception error;
    }

    private class HistoryTask extends AsyncTask<Void, Void, HistoryResult> {
        @Override
        protected HistoryResult doInBackground(Void... params) {
            HistoryResult result = new HistoryResult();
            try {
                Account account = AuthManager.getInstance().getAccount();
                Auth auth = AuthManager.getInstance().getAuth();
                if (account == null || auth == null) {
                    result.error = new Exception("Not logged in");
                } else {
                    result.videos = auth.getHistory(account);
                }
            } catch (Exception e) {
                e.printStackTrace();
                result.error = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(HistoryResult result) {
            historyLoading = false;
            if (isDestroyedFlag) return;
            if (!isLoggedIn()) return;
            if (result.error != null) {
                historyLoaded = true;
                historyVideos = new ArrayList<VideoInfo>();
                updateHistoryUI();
            } else {
                historyVideos = result.videos != null ? result.videos : new ArrayList<VideoInfo>();
                historyLoaded = true;
                populateHistoryStrip();
            }
        }
    }

    private class HistoryResult {
        List<VideoInfo> videos;
        Exception error;
    }
}