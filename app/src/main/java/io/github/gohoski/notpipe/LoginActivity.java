package io.github.gohoski.notpipe;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import io.github.gohoski.notpipe.api.Auth;
import io.github.gohoski.notpipe.auth.AuthManager;
import io.github.gohoski.notpipe.api.Yt2009;
import io.github.gohoski.notpipe.config.ConfigManager;
import io.github.gohoski.notpipe.data.Account;
import io.github.gohoski.notpipe.data.AuthPrompt;

/**
 * Created by Gleb on 29.08.2026.
 * Handles device-code login for yt2009 and stub for YTFixer.
 */
public class LoginActivity extends Activity {

    private LinearLayout stageSelect;
    private LinearLayout stageCode;
    private RadioGroup authBackendGroup;
    private RadioButton radioYtfixer;
    private RadioButton radioYt2009;
    private LinearLayout instanceContainer;
    private EditText instanceUrlEdit;
    private Button btnStartAuth;
    private TextView authInstructions;
    private ImageView authQrImage;
    private TextView authUrlDisplay;
    private TextView authCodeDisplay;
    private Button btnCheckStatus;
    private ProgressBar authLoading;

    private AuthPrompt currentPrompt;
    private Auth currentAuth;
    private String currentInstanceUrl;

    private boolean isDestroyedFlag = false;

    private static class RetainedState {
        AuthPrompt prompt;
        Auth auth;
        String instanceUrl;
        boolean inCodeStage;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        RetainedState state = new RetainedState();
        state.prompt = currentPrompt;
        state.auth = currentAuth;
        state.instanceUrl = currentInstanceUrl;
        state.inCodeStage = stageCode != null && stageCode.getVisibility() == View.VISIBLE;
        return state;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        stageSelect = (LinearLayout) findViewById(R.id.stage_select);
        stageCode = (LinearLayout) findViewById(R.id.stage_code);
        authBackendGroup = (RadioGroup) findViewById(R.id.auth_backend_group);
        radioYtfixer = (RadioButton) findViewById(R.id.radio_ytfixer);
        radioYt2009 = (RadioButton) findViewById(R.id.radio_yt2009);
        instanceContainer = (LinearLayout) findViewById(R.id.instance_container);
        instanceUrlEdit = (EditText) findViewById(R.id.instance_url);
        btnStartAuth = (Button) findViewById(R.id.btn_start_auth);
        authInstructions = (TextView) findViewById(R.id.auth_instructions);
        authQrImage = (ImageView) findViewById(R.id.auth_qr_image);
        authUrlDisplay = (TextView) findViewById(R.id.auth_url_display);
        authCodeDisplay = (TextView) findViewById(R.id.auth_code_display);
        btnCheckStatus = (Button) findViewById(R.id.btn_check_status);
        authLoading = (ProgressBar) findViewById(R.id.auth_loading);

        try {
            List<String> yt2009Instances = ConfigManager.getInstance().getConfig().getYt2009Instances();
            if (yt2009Instances != null && !yt2009Instances.isEmpty()) {
                instanceUrlEdit.setText(yt2009Instances.get(0));
            } else {
                instanceUrlEdit.setHint(R.string.yt2009_url_hint);
            }
        } catch (Exception ignored) {
            instanceUrlEdit.setHint(R.string.yt2009_url_hint);
        }

        authBackendGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radio_ytfixer) {
                } else {
                    instanceContainer.setVisibility(View.VISIBLE);
                }
            }
        });

        btnStartAuth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int checkedId = authBackendGroup.getCheckedRadioButtonId();
                if (checkedId == R.id.radio_ytfixer) {
                    Toast.makeText(LoginActivity.this, R.string.ytfixer_not_implemented, Toast.LENGTH_LONG).show();
                    return;
                }

                // yt2009 flow
                String url = instanceUrlEdit.getText().toString().trim();
                if (url.length() == 0) {
                    try {
                        List<String> list = ConfigManager.getInstance().getConfig().getYt2009Instances();
                        if (list != null && !list.isEmpty()) {
                            url = list.get(0);
                            instanceUrlEdit.setText(url);
                        } else {
                            Toast.makeText(LoginActivity.this, R.string.incorrect_url, Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } catch (Exception e) {
                        Toast.makeText(LoginActivity.this, R.string.incorrect_url, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                // Ensure trailing slash removed and validate
                url = AuthManager.normalizeUrl(url);
                if (!AuthManager.isValidInstanceUrl(url)) {
                    Toast.makeText(LoginActivity.this, R.string.incorrect_url, Toast.LENGTH_SHORT).show();
                    return;
                }

                currentInstanceUrl = url;
                setLoading(true);
                new StartAuthTask().execute(url);
            }
        });

        btnCheckStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPrompt == null || currentAuth == null) {
                    Toast.makeText(LoginActivity.this, R.string.no_auth_session, Toast.LENGTH_SHORT).show();
                    return;
                }
                setLoading(true);
                new CheckAuthTask().execute();
            }
        });

        // Restore retained state after rotation
        RetainedState retained = (RetainedState) getLastNonConfigurationInstance();
        if (retained != null) {
            currentPrompt = retained.prompt;
            currentAuth = retained.auth;
            currentInstanceUrl = retained.instanceUrl;
            if (retained.inCodeStage && currentPrompt != null) {
                showCodeStage(currentPrompt);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyedFlag = true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (stageCode != null && stageCode.getVisibility() == View.VISIBLE) {
                stageCode.setVisibility(View.GONE);
                stageSelect.setVisibility(View.VISIBLE);
                currentPrompt = null;
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void setLoading(boolean loading) {
        if (authLoading != null) {
            authLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnStartAuth != null) {
            btnStartAuth.setEnabled(!loading);
        }
        if (btnCheckStatus != null) {
            btnCheckStatus.setEnabled(!loading);
        }
    }

    private void showCodeStage(AuthPrompt prompt) {
        stageSelect.setVisibility(View.GONE);
        stageCode.setVisibility(View.VISIBLE);

        if (authInstructions != null) {
            try {
                authInstructions.setText(getString(prompt.instructionStringRes));
            } catch (Exception e) {
                authInstructions.setText(getString(R.string.auth_desc_yt2009));
            }
        }
        if (authUrlDisplay != null) {
            authUrlDisplay.setText(prompt.verificationUrl != null ? prompt.verificationUrl : prompt.instanceUrl);
        }
        if (authCodeDisplay != null) {
            authCodeDisplay.setText(prompt.userCode != null ? prompt.userCode : "");
        }
        if (authQrImage != null) {
            if (prompt.hasQrCode()) {
                try {
                    authQrImage.setImageBitmap(prompt.getQrBitmap());
                    authQrImage.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    authQrImage.setVisibility(View.GONE);
                }
            } else {
                authQrImage.setVisibility(View.GONE);
            }
        }
    }

    private class StartAuthTask extends AsyncTask<String, Void, StartResult> {
        @Override
        protected StartResult doInBackground(String... params) {
            StartResult result = new StartResult();
            String url = params[0];
            result.instanceUrl = url;
            try {
                Auth auth = new Yt2009(url);
                AuthPrompt prompt = auth.startAuth();
                result.prompt = prompt;
                result.auth = auth;
            } catch (Exception e) {
                result.error = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(StartResult result) {
            if (isDestroyedFlag) return;
            setLoading(false);
            if (result.error != null) {
                String msg = result.error.getMessage();
                if (msg == null || msg.length() == 0) msg = result.error.toString();
                Toast.makeText(LoginActivity.this, getString(R.string.auth_failed, msg), Toast.LENGTH_LONG).show();
                result.error.printStackTrace();
            } else if (result.prompt != null) {
                currentPrompt = result.prompt;
                currentAuth = result.auth;
                currentInstanceUrl = result.instanceUrl;
                showCodeStage(result.prompt);
                Toast.makeText(LoginActivity.this, R.string.auth_status_inited, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class StartResult {
        AuthPrompt prompt;
        Auth auth;
        String instanceUrl;
        Exception error;
    }

    private class CheckAuthTask extends AsyncTask<Void, Void, CheckResult> {
        @Override
        protected CheckResult doInBackground(Void... params) {
            CheckResult result = new CheckResult();
            try {
                boolean ok = currentAuth.checkAuthStatus(currentPrompt);
                result.success = ok;
                if (ok) {
                    Account acc = currentAuth.completeAuth(currentPrompt);
                    result.account = acc;
                }
            } catch (Exception e) {
                result.error = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(CheckResult result) {
            if (isDestroyedFlag) return;
            setLoading(false);
            if (result.error != null) {
                String msg = result.error.getMessage();
                if (msg == null || msg.length() == 0) msg = result.error.toString();
                Toast.makeText(LoginActivity.this, getString(R.string.auth_check_failed, msg), Toast.LENGTH_LONG).show();
                result.error.printStackTrace();
            } else if (result.success && result.account != null) {
                try {
                    AuthManager.getInstance().saveAccount(result.account);
                } catch (IllegalStateException e) {
                    // If AuthManager not initialized, fallback to direct save via prefs
                    // However This should not happen because NotPipe inits it
                    e.printStackTrace();
                }
                Toast.makeText(LoginActivity.this, R.string.auth_success, Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            } else if (!result.success) {
                Toast.makeText(LoginActivity.this, R.string.auth_status_waiting, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(LoginActivity.this, R.string.auth_unknown_state, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class CheckResult {
        boolean success;
        Account account;
        Exception error;
    }
}
