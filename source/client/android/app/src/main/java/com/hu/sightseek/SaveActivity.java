package com.hu.sightseek;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class SaveActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_save);

        // Retrieve data
        Bundle extras = getIntent().getExtras();

        String polyline = extras.getString("polyline");
        String startTime = extras.getString("starttime");
        String endTime = extras.getString("endtime");
        double elapsedTime = extras.getDouble("elapsedtime");
        double totalDist = extras.getDouble("dist");

        // Save button
        Button saveButton = findViewById(R.id.save_savebtn);
        saveButton.setOnClickListener(view -> {
            // Create JSON
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("id", new Random().nextInt(9999999)); // TODO
                jsonObject.put("polyline", polyline);
                jsonObject.put("starttime", startTime);
                jsonObject.put("endtime", endTime);
                jsonObject.put("elapsedtime", elapsedTime);
                jsonObject.put("dist", totalDist);
            }
            catch(JSONException e) {
                e.printStackTrace();
            }

            System.out.println(jsonObject);

            // TEMPORARY!!! EXPORT TO EXTERNAL STORAGE
            String filename = "newroute" + new Random().nextInt(9999999) + ".json";
            File exportDir = this.getExternalFilesDir(null);
            File file = new File(exportDir, filename);

            try(Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(jsonObject.toString());
            }
            catch(IOException e) {
                e.printStackTrace();
            }
        });

        // Discard button
        Button discardButton = findViewById(R.id.save_discardbtn);
        discardButton.setOnClickListener(view -> {

        });
    }
}