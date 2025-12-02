package com.hu.sightseek.broadcast;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class IdeaBroadcaster {
    public static final String ACTION_ATTRACTIONS_UPDATED = "ATTRACTIONS_UPDATED";

    public static void sendUpdate(Context context) {
        Intent intent = new Intent(ACTION_ATTRACTIONS_UPDATED);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
