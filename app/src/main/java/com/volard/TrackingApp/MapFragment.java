package com.volard.TrackingApp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
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
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;

public class MapFragment extends Fragment  implements GoogleMap.OnMarkerClickListener,
        OnMapReadyCallback {

    // Debug
    // private final String TAG = "TRACKING_MAP";
    private final String TAG = BluetoothService.TAG;


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

        // Initial location to show
        LatLng Irkutsk = new LatLng(52.25077493211072, 104.34563132285335);
        // Set opportunity to see indoor map
        googleMap.setIndoorEnabled(true);

        // Set the marker
        Objects.requireNonNull(googleMap.addMarker(new MarkerOptions()
                .position(Irkutsk)
                .icon(BitmapDescriptorFactory.defaultMarker(getRandomHue()))
                .title("Солнечная дорога"))).showInfoWindow();
        googleMap.moveCamera(CameraUpdateFactory.zoomTo(15));
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(Irkutsk));

        googleMap.setOnMarkerClickListener(this);

        // Set clean map style
        try {
            // NOTE idk how getBaseActivity() will work when the parent Activity is another Fragment
            InputStream jsonContent = requireActivity().getBaseContext().getAssets().open("simple_map_theme.json");
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


    @Override
    public boolean onMarkerClick(final Marker marker) {
        final Handler handler = new Handler();

        handler.post(new Runnable() {
            @Override
            public void run() {

            }
        });

        Log.i(TAG, "Marker " + marker.getTitle() + " was clicked");
//        Intent intent = new Intent(requireActivity().getBaseContext(),
//                TargetActivity.class);
//        intent.putExtra("message", message);
//        getActivity().startActivity(intent);

        // We return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
        return false;
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

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