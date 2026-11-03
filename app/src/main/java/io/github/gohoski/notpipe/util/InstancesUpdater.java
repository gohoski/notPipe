package io.github.gohoski.notpipe.util;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.notpipe.R;
import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.config.Config;
import io.github.gohoski.notpipe.config.ConfigManager;
import io.github.gohoski.notpipe.http.HttpClient;
import io.github.gohoski.notpipe.http.HttpRequest;
import io.github.gohoski.notpipe.ui.Loading;

/**
 * Created by Qwen on 2026.
 * Utility class for updating API instances from a remote URL.
 */
public class InstancesUpdater {

    private final Context context;
    private final Config config;
    private final OnInstancesUpdatedListener listener;

    public InstancesUpdater(Context context, OnInstancesUpdatedListener listener) {
        this.context = context;
        this.config = ConfigManager.getInstance().getConfig();
        this.listener = listener;
    }

    /**
     * Updates instances from the configured URL.
     * Shows a loading dialog and toast messages for errors.
     */
    public void updateInstances() {
        new UpdateInstancesTask().execute();
    }

    /**
     * Callback interface for when instances have been updated.
     */
    public interface OnInstancesUpdatedListener {
        void onInstancesUpdated();
    }

    private class UpdateInstancesTask extends AsyncTask<Void, Void, Boolean> {
        private Loading load;
        private Exception error;

        @Override
        protected void onPreExecute() {
            load = new Loading(context, R.string.updating_instances);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                JSONObject obj = JSON.getObject(HttpClient.executeToString(
                        new HttpRequest(config.getInstancesUpdateUrl())));
                JSONArray inv = obj.getArray("invidious"),
                        yt2009 = obj.getArray("yt2009"),
                        piped = obj.getArray("piped"),
                        typetype = obj.getNullableArray("typetype");
                List<String> instances = new ArrayList<String>();
                for (int i = 0; i < inv.size(); i++) {
                    instances.add(inv.getString(i));
                }
                config.setInvidiousInstances(instances);

                instances = new ArrayList<String>();
                for (int i = 0; i < yt2009.size(); i++) {
                    instances.add(yt2009.getString(i));
                }
                config.setYt2009Instances(instances);

                instances = new ArrayList<String>();
                for (int i = 0; i < piped.size(); i++) {
                    instances.add(piped.getString(i));
                }
                config.setPipedInstances(instances);

                if (typetype != null) {
                    instances = new ArrayList<String>();
                    for (int i = 0; i < typetype.size(); i++) {
                        instances.add(typetype.getString(i));
                    }
                    config.setTypeTypeInstances(instances);
                }

                JSONArray ytApiLegacy = obj.getArray("ytapilegacy");
                instances = new ArrayList<String>();
                for (int i = 0; i < ytApiLegacy.size(); i++) {
                    instances.add(ytApiLegacy.getString(i));
                }
                config.setYtApiLegacyInstances(instances);

                config.setLastUpdate(System.currentTimeMillis());
                ConfigManager.getInstance().saveConfig(config);

                Manager.getInstance().reloadInstances();
                return true;
            } catch (Exception e) {
                error = e;
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (load != null) {
                load.dismiss();
            }
            if (!success) {
                Toast.makeText(context, context.getString(R.string.update_failed, error.getMessage()), Toast.LENGTH_LONG).show();
            }
            if (listener != null) {
                listener.onInstancesUpdated();
            }
        }
    }
}
