package com.hu.sightseek;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.hu.sightseek.activity.IdeaActivity;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

public class SelectLocationFragment extends DialogFragment {
    private MapView mapView;
    private Marker marker;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context ctx = inflater.getContext();
        View view = inflater.inflate(R.layout.fragment_select_location, container, false);

        mapView = view.findViewById(R.id.locationselectpopup_map);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);

        marker = new Marker(mapView);

        // Try to get location
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if(!(lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                || ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {

            GeoPoint point = new GeoPoint(47.499, 19.044);
            mapView.getController().setCenter(point);
            refreshMarker(point);
        }
        else {
            FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(ctx);
            fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
                if(location != null) {
                    GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
                    mapView.getController().setCenter(point);
                    refreshMarker(point);
                }
            });
        }
        mapView.invalidate();

        MapEventsOverlay eventsOverlay = getMapEventsOverlay();
        mapView.getOverlays().add(eventsOverlay);

        Button selectButton = view.findViewById(R.id.locationselectpopup_selectbtn);
        selectButton.setOnClickListener(v -> {
            ((IdeaActivity) requireActivity()).onNewLocationSelected(marker.getPosition());
            dismiss();
        });

        return view;
    }

    private void refreshMarker(GeoPoint point) {
        mapView.getOverlays().remove(marker);

        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        mapView.getOverlays().add(marker);
        mapView.invalidate();
    }


    @NonNull
    private MapEventsOverlay getMapEventsOverlay() {
        MapEventsReceiver mReceiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                refreshMarker(point);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };

        return new MapEventsOverlay(mReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }
}
