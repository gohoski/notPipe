package io.github.gohoski.notpipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.config.Config;
import io.github.gohoski.notpipe.config.ConfigManager;
import io.github.gohoski.notpipe.ui.InstanceSection;
import io.github.gohoski.notpipe.util.InstancesUpdater;

/**
 * Settings activity for managing API instances and app settings.
 * API instances are added programmatically, general settings are in the layout.
 */
public class SettingsActivity extends Activity implements InstancesUpdater.OnInstancesUpdatedListener {
    private LinearLayout apiInstancesContainer;
    private CheckBox updateInstancesChk, streamPlaybackChk, convertVideosChk, asyncUriChk;
    private CheckBox fullscreenRotateChk;
    private CheckBox hideShortsRecChk, hideShortsSubsChk;
    private EditText shortLengthEdit;
    private LinearLayout updateInstancesLayout, convertLayout;
    private EditText instancesUrlEdit;
    private Spinner updateFreqSpinner, playerSpinner, codecSpinner;
    private Spinner qualitySpinner;
    private InstanceSection invidiousSection;
    private InstanceSection ytApiLegacySection;
    private InstanceSection yt2009Section;
    private InstanceSection pipedSection;
    private InstanceSection typetypeSection;

    private ConfigManager configManager;
    private Config config;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        context = this;
        configManager = ConfigManager.getInstance();
        config = configManager.getConfig();

        apiInstancesContainer = (LinearLayout) findViewById(R.id.apiInstancesContainer);
        updateInstancesChk = (CheckBox) findViewById(R.id.update_chk);
        updateInstancesLayout = (LinearLayout) findViewById(R.id.update_instances);
        instancesUrlEdit = (EditText) findViewById(R.id.instances_url);
        updateFreqSpinner = (Spinner) findViewById(R.id.update_spinner);
        playerSpinner = (Spinner) findViewById(R.id.player_spinner);
        streamPlaybackChk = (CheckBox) findViewById(R.id.stream_playback_chk);
        qualitySpinner = (Spinner) findViewById(R.id.quality_spinner);
        qualitySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if ("1080p".equals(parent.getItemAtPosition(position))) {
                    new AlertDialog.Builder(context)
                            .setTitle(android.R.string.dialog_alert_title)
                            .setMessage(R.string.experimental_1080)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        codecSpinner = (Spinner) findViewById(R.id.codec_spinner);
        convertVideosChk = (CheckBox) findViewById(R.id.convert_chk);
        convertLayout = (LinearLayout) findViewById(R.id.codec_layout);
        asyncUriChk = (CheckBox) findViewById(R.id.async_uri_chk);
        fullscreenRotateChk = (CheckBox) findViewById(R.id.fullscreen_rotate_chk);
        hideShortsRecChk = (CheckBox) findViewById(R.id.hide_shorts_rec_chk);
        hideShortsSubsChk = (CheckBox) findViewById(R.id.hide_shorts_subs_chk);
        shortLengthEdit = (EditText) findViewById(R.id.short_length_edit);

        hideShortsRecChk.setChecked(config.isHideShortsRecommendations());
        hideShortsSubsChk.setChecked(config.isHideShortsSubscriptions());
        shortLengthEdit.setText(String.valueOf(config.getShortLengthSeconds()));

        updateInstancesChk.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateInstancesLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });
        convertVideosChk.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                        Toast.makeText(context, R.string.sd_card, Toast.LENGTH_LONG).show();
                        convertVideosChk.setChecked(false);
                        return;
                    }
                    convertLayout.setVisibility(View.VISIBLE);
                    setSpinnerSelection(qualitySpinner, "360p");
                    playerSpinner.setSelection(0);
                    qualitySpinner.setEnabled(false);
                } else {
                    convertLayout.setVisibility(View.GONE);
                    qualitySpinner.setEnabled(true);
                    if (NotPipe.SDK < 11) {
                        playerSpinner.setSelection(1);
                    }
                }
            }
        });

        streamPlaybackChk.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                        Toast.makeText(context, R.string.sd_card, Toast.LENGTH_LONG).show();
                        streamPlaybackChk.setChecked(true);
                    }
                }
            }
        });

        updateInstancesChk.setChecked(config.isUpdateInstancesFromUrl());
        updateInstancesLayout.setVisibility(config.isUpdateInstancesFromUrl() ? View.VISIBLE : View.GONE);
        instancesUrlEdit.setText(config.getInstancesUpdateUrl());
        updateFreqSpinner.setSelection(config.getUpdateFrequency());
        playerSpinner.setSelection(config.isUseExternalPlayer() ? 1 : 0);
        streamPlaybackChk.setChecked(config.isStreamPlayback());
        setSpinnerSelection(qualitySpinner, config.getPreferredQuality() + "p");
        convertVideosChk.setChecked(config.isConvertVideos());
        codecSpinner.setSelection(config.getConvertCodec());
        asyncUriChk.setChecked(config.isAsyncSetVideoUri());
        fullscreenRotateChk.setChecked(config.isFullscreenRotateLandscape());

        if (config.isConvertVideos()) {
            qualitySpinner.setEnabled(false);
        }

        invidiousSection = new InstanceSection(context, "Invidious", config.getInvidiousInstances());
        yt2009Section = new InstanceSection(context, "yt2009", config.getYt2009Instances());
        pipedSection = new InstanceSection(context, "Piped", config.getPipedInstances());
        typetypeSection = new InstanceSection(context, "TypeType", config.getTypeTypeInstances());
        ytApiLegacySection = new InstanceSection(context, "YtAPILegacy", config.getYtApiLegacyInstances());

        apiInstancesContainer.addView(invidiousSection);
        apiInstancesContainer.addView(yt2009Section);
        apiInstancesContainer.addView(pipedSection);
        apiInstancesContainer.addView(typetypeSection);
        apiInstancesContainer.addView(ytApiLegacySection);

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(context, MainActivity.class));
                finish();
            }
        });
        findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                startActivity(new Intent(context, MainActivity.class));
                finish();
            }
        });
        findViewById(R.id.update_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                new InstancesUpdater(context, SettingsActivity.this).updateInstances();
            }
        });
    }

    private void saveSettings() {
        config.setUpdateInstancesFromUrl(updateInstancesChk.isChecked());
        config.setInstancesUpdateUrl(instancesUrlEdit.getText().toString().trim());
        config.setUpdateFrequency(updateFreqSpinner.getSelectedItemPosition());
        config.setUseExternalPlayer(playerSpinner.getSelectedItemPosition() == 1);
        config.setStreamPlayback(streamPlaybackChk.isChecked());
        config.setPreferredQuality(((String) qualitySpinner.getSelectedItem()).replace("p", ""));
        config.setConvertVideos(convertVideosChk.isChecked());
        config.setConvertCodec(codecSpinner.getSelectedItemPosition());
        config.setAsyncSetVideoUri(asyncUriChk.isChecked());
        config.setFullscreenRotateLandscape(fullscreenRotateChk.isChecked());
        config.setHideShortsRecommendations(hideShortsRecChk.isChecked());
        config.setHideShortsSubscriptions(hideShortsSubsChk.isChecked());
        int shortLength = 90;
        try {
            shortLength = Integer.parseInt(shortLengthEdit.getText().toString().trim());
        } catch (Exception ignored) {}
        config.setShortLengthSeconds(shortLength);

        config.setInvidiousInstances(invidiousSection.getInstances());
        config.setYt2009Instances(yt2009Section.getInstances());
        config.setPipedInstances(pipedSection.getInstances());
        config.setTypeTypeInstances(typetypeSection.getInstances());
        config.setYtApiLegacyInstances(ytApiLegacySection.getInstances());

        configManager.saveConfig(config);
        Manager.getInstance().reloadInstances();
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    @Override
    public void onInstancesUpdated() {
        config = configManager.getConfig();
        invidiousSection.setInstances(config.getInvidiousInstances());
        yt2009Section.setInstances(config.getYt2009Instances());
        pipedSection.setInstances(config.getPipedInstances());
        typetypeSection.setInstances(config.getTypeTypeInstances());
        ytApiLegacySection.setInstances(config.getYtApiLegacyInstances());
        Toast.makeText(this, R.string.ins_upd, Toast.LENGTH_SHORT).show();
    }
}