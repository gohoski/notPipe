package io.github.gohoski.notpipe.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.notpipe.R;

/**
 * Reusable component for managing API instances.
 * Displays a section with title, list of instances, and add functionality.
 */
public class InstanceSection extends LinearLayout {

    private TextView titleView;
    private LinearLayout instancesContainer;
    private LinearLayout addInstanceRow;
    private EditText newInstanceEditText;
    private ImageButton addInstanceButton;

    private String apiName;
    private List<String> instances;
    private LayoutInflater inflater;
    private Context context;

    public InstanceSection(Context context, String apiName, List<String> instances) {
        super(context);
        this.context = context;
        this.apiName = apiName;
        this.instances = instances;
        this.inflater = LayoutInflater.from(context);

        setOrientation(VERTICAL);
        init();
    }

    private void init() {
        inflater.inflate(R.layout.instance_section, this, true);

        titleView = (TextView) findViewById(R.id.sectionTitle);
        instancesContainer = (LinearLayout) findViewById(R.id.instancesContainer);
        addInstanceRow = (LinearLayout) findViewById(R.id.addInstanceRow);
        newInstanceEditText = (EditText) findViewById(R.id.newInstanceEditText);
        addInstanceButton = (ImageButton) findViewById(R.id.addInstanceButton);

        titleView.setText(apiName);

        loadInstances();
        setupAddButton();
    }

    private void loadInstances() {
        instancesContainer.removeAllViews();
        if (instances != null) {
            for (int i = 0; i < instances.size(); i++) {
                String url = instances.get(i);
                addInstanceRow(url);
            }
        }
    }

    private void addInstanceRow(final String url) {
        final View rowView = inflater.inflate(R.layout.instance_row, instancesContainer, false);

        EditText editText = (EditText) rowView.findViewById(R.id.instanceEditText);
        ImageButton removeButton = (ImageButton) rowView.findViewById(R.id.removeInstanceButton);

        editText.setText(url);

        removeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                removeInstance(rowView, url);
            }
        });

        instancesContainer.addView(rowView);
    }

    private void setupAddButton() {
        addInstanceButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewInstance();
            }
        });
    }

    private void addNewInstance() {
        CharSequence text = newInstanceEditText.getText();
        String url = (text != null) ? text.toString().trim() : "";
        if (url.length() == 0) {
            Toast.makeText(context, R.string.incorrect_url, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String[] parts = url.split(",");
            StringBuilder normalized = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.length() == 0) {
                    throw new java.net.MalformedURLException("empty part");
                }
                if (!part.startsWith("http://") && !part.startsWith("https://")) {
                    part = "http://" + part;
                }
                if (part.endsWith("/")) {
                    part = part.substring(0, part.length() - 1);
                }
                new java.net.URL(part);
                if (i > 0) normalized.append(",");
                normalized.append(part);
            }
            url = normalized.toString();
        } catch (java.net.MalformedURLException e) {
            Toast.makeText(context, R.string.incorrect_url, Toast.LENGTH_SHORT).show();
            return;
        }

        // Refresh the list to check against what's currently in the layout
        instances = getInstances();

        if (instances.contains(url)) {
            Toast.makeText(context, R.string.instance_exist, Toast.LENGTH_SHORT).show();
            return;
        }
        addInstanceRow(url);
        instances.add(url);
        newInstanceEditText.setText("");
    }

    private void removeInstance(View rowView, String url) {
        instancesContainer.removeView(rowView);
    }

    /**
     * Reads directly from the dynamically created Views to ensure
     * manual edits made by the user are properly saved.
     */
    public List<String> getInstances() {
        List<String> updatedInstances = new ArrayList<String>();

        for (int i = 0; i < instancesContainer.getChildCount(); i++) {
            View rowView = instancesContainer.getChildAt(i);
            EditText editText = (EditText) rowView.findViewById(R.id.instanceEditText);

            if (editText != null) {
                CharSequence text = editText.getText();
                String url = (text != null) ? text.toString().trim() : "";

                if (url.length() > 0) {
                    // Prevent saving duplicates if the user accidentally edited two fields to be the same
                    if (!updatedInstances.contains(url)) {
                        updatedInstances.add(url);
                    }
                }
            }
        }

        instances = updatedInstances; // Sync internal memory tracker
        return instances;
    }

    /**
     * Updates the internal instances list and refreshes the UI.
     */
    public void setInstances(List<String> instances) {
        this.instances = instances;
        loadInstances();
    }
}