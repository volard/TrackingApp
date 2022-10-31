package com.volard.TrackingApp;

import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;

public class MapFragment extends Fragment  implements GoogleMap.OnMarkerClickListener,
        OnMapReadyCallback {

    // Debug
    // private final String TAG = "TRACKING_MAP";
    private final String TAG = BluetoothService.TAG;

    // Member fields
    private ArrayList<Marker> markers = new ArrayList<>();
    private GoogleMap mGoogleMap = null;
    private boolean showTitles = true;
    private final Handler mHandler = new Handler();
    private Context mContext = null;


    /**
     * Returns random float value representing HUE value
     * @return random HUE float value
     */
    private float getRandomHue(){
                            final float MIN = 0;
                            final float MAX = 360;

                            Random random = new SecureRandom();
                            return MIN + random.nextFloat() * (MAX - MIN);
                            }


    /**
    * Manipulates the map once available.
    * This callback is triggered when the map is ready to be used.
    * This is where we can add markers or lines, add listeners or move the camera.
    * In this case, we just add a marker near Sydney, Australia.
    * If Google Play services is not installed on the device, the user will be prompted to
    * install it inside the SupportMapFragment. This method will only be triggered once the
    * user has installed Google Play services and returned to the app.
    */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.mGoogleMap = googleMap;

        // Initial location to show
//        LatLng Irkutsk = new LatLng(52.25077493211072, 104.34563132285335);
        // Set opportunity to see indoor map
//        googleMap.setIndoorEnabled(true);

        // Set the marker
//        Objects.requireNonNull(googleMap.addMarker(new MarkerOptions()
//                .position(Irkutsk)
//                .icon(BitmapDescriptorFactory.defaultMarker(getRandomHue()))
//                .title("Sunny road"))).showInfoWindow();

        googleMap.moveCamera(CameraUpdateFactory.zoomTo(15));
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(52.251080, 104.356545)));

        googleMap.setOnMarkerClickListener(this);

        // Set map style
        try {
            String themeFileName = "mapstyle_night.json";
//            String themeFileName = "simple_map_theme.json";
            InputStream jsonContent = mContext.getAssets().open(themeFileName);
            String jsonStyle;

            try (Scanner scanner = new Scanner(jsonContent, StandardCharsets.UTF_8.name())) {
                jsonStyle = scanner.useDelimiter("\\A").next();
            }
            if (jsonStyle == null) throw new NullPointerException();

            MapStyleOptions style = new MapStyleOptions(jsonStyle);
            googleMap.setMapStyle(style);

        } catch (IOException e) {
            Log.e(TAG, "Style json asset file is unavailable or doesn't exist");
        } catch (NullPointerException e){
            Log.e(TAG, "Error parsing json asset style file");
        }
    }


    public void animateMarker(final Marker marker, final LatLng toPosition,
                              final boolean hideMarker) {
        final long start = SystemClock.uptimeMillis();
        Projection proj = mGoogleMap.getProjection();
        Point startPoint = proj.toScreenLocation(marker.getPosition());
        final LatLng startLatLng = proj.fromScreenLocation(startPoint);
        final long duration = 500;

        final Interpolator interpolator = new LinearInterpolator();

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed
                        / duration);
                double lng = t * toPosition.longitude + (1 - t)
                        * startLatLng.longitude;
                double lat = t * toPosition.latitude + (1 - t)
                        * startLatLng.latitude;
                marker.setPosition(new LatLng(lat, lng));

                if (t < 1.0) {
                    // Post again 16ms later.
                    mHandler.postDelayed(this, 16);
                } else {
                    if (hideMarker) {
                        marker.setVisible(false);
                    } else {
                        marker.setVisible(true);
                    }
                }
            }
        });
    }



    public void toggleTitles(){
        if (showTitles){
            for (Marker marker :
                    markers) {
                marker.hideInfoWindow();
            }
            showTitles = false;
            return;
        }
        for (Marker marker: markers) {
            marker.showInfoWindow();
        }
        showTitles = true;
    }


    // NOTE: we will use _title_ to show human-readable name of a marker
    // NOTE: we will use _tag_ to show defined by us id of a marker

    private void createNewMarker(int id, double latitude, double longitude){
        Marker newMarker = mGoogleMap.addMarker(new MarkerOptions()
                .position(new LatLng(latitude, longitude))
                .icon(BitmapDescriptorFactory.defaultMarker(getRandomHue()))
        );
        if (newMarker != null){
            newMarker.setTag(id);
            markers.add(newMarker);

            // This causes the marker to bounce into position where it was created
            final long start = SystemClock.uptimeMillis();
            final long duration = 500;

            final Interpolator interpolator = new BounceInterpolator();

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    long elapsed = SystemClock.uptimeMillis() - start;
                    float t = Math.max(
                            1 - interpolator.getInterpolation((float) elapsed / duration), 0);
                    newMarker.setAnchor(0.5f, 1.0f + 2 * t);

                    if (t > 0.0) {
                        // Post again 16ms later.
                        mHandler.postDelayed(this, 16);
                    }
                }
            });


            if (showTitles){
                newMarker.showInfoWindow();
            }
            Log.i(TAG, "New marker was created with tag = " + Objects.requireNonNull(newMarker.getTag()).toString());
        }
        else {
            Log.e(TAG, "map: Can't create the new marker with tag = " + id);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext,
                            "Не могу создать новую метку, хотя данные поступили",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }


    public void updateMarkerPosition(int id, double latitude, double longitude){
        for (Marker marker :
                markers) {
            if ((Integer)marker.getTag() == id){
                animateMarker(marker, new LatLng(latitude, longitude), false);
                return;
            }
        }

        createNewMarker(id, latitude, longitude);
//        Log.e(TAG, "map: Can't update position of the marker with id = " + id);
//        mHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                Toast.makeText(mContext,
//                        "Не могу обновить позицию метки, хотя данные поступили",
//                        Toast.LENGTH_SHORT).show();
//            }
//        });
    }



    // ======================== OVERRIDES ========================

    @Override
    public boolean onMarkerClick(final Marker marker) {

        mHandler.post(new Runnable() {
            @Override
            public void run() {

            }
        });

        Bundle result = new Bundle();
        result.putString("bundleKey", Objects.requireNonNull(marker.getTag()).toString());
        getParentFragmentManager().setFragmentResult("requestKey", result);

        // We return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
        return false;
    }


    // The onCreateView method is called when Fragment should create its View object hierarchy,
    // either dynamically or via XML layout inflation.
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Defines the xml file for the fragment
        mContext = requireActivity().getBaseContext();

        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    // This event is triggered soon after onCreateView().
    // Any view setup should occur here.  E.g., view lookups and attaching view listeners.
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

    }
}