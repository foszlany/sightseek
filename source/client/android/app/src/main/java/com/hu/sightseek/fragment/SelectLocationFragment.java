package com.hu.sightseek.fragment;

import static com.hu.sightseek.utils.SightseekGenericUtils.setupZoomSettings;

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
import com.hu.sightseek.R;
import com.hu.sightseek.activity.IdeaActivity;
import com.hu.sightseek.utils.SightseekGenericUtils;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

public class SelectLocationFragment extends DialogFragment {
    private GeoPoint referencePoint;
    private MapView mapView;
    private Marker marker;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context ctx = inflater.getContext();
        View view = inflater.inflate(R.layout.fragment_select_location, container, false);

        // Retrieve previous location if possible
        referencePoint = null;
        if(getArguments() != null) {
            referencePoint = getArguments().getParcelable("referencePoint");
        }

        // Map
        mapView = view.findViewById(R.id.locationselectpopup_map);
        setupZoomSettings(mapView, 11.0);

        // Marker
        marker = new Marker(mapView);
        marker.setInfoWindow(null);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);

        // Reference point exists
        if(referencePoint != null) {
            mapView.getController().setCenter(referencePoint);
            refreshMarker(referencePoint);
        }
        // Default to Budapest
        else if(!(lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                || ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {

            referencePoint = new GeoPoint(SightseekGenericUtils.BUDAPEST_LATITUDE, SightseekGenericUtils.BUDAPEST_LONGITUDE);
            mapView.getController().setCenter(referencePoint);
            refreshMarker(referencePoint);
        }
        // Try to get location
        else {
            FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(ctx);
            fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
                if(location != null) {
                    referencePoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                    mapView.getController().setCenter(referencePoint);
                    refreshMarker(referencePoint);
                }
            });
        }

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
        marker.setPosition(point);
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
