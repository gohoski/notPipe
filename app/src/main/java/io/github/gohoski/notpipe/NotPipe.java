package io.github.gohoski.notpipe;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.auth.AuthManager;
import io.github.gohoski.notpipe.config.ConfigManager;

/**
 * Created by Gleb on 25.01.2026.
 * here we can also store some useful values that may be reused in the app frequently
 */

public class NotPipe extends Application {
    public static final int SDK = Integer.parseInt(Build.VERSION.SDK);
    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = this;
        // Let's initialize our one-time stuffs here :3
        // Initialize ConfigManager first (Manager depends on it)
        ConfigManager.init(this);
        ConfigManager.getInstance().ensureInstancesConfigured();
        AuthManager.init(this);
        Manager.init();
        SSLDisabler.disableSSLCertificateChecking();
        if (NotPipe.SDK < 9) {
            System.setProperty("http.keepAlive", "false");
        }
    }

    public static Context getAppContext() {
        return appContext;
    }
}
