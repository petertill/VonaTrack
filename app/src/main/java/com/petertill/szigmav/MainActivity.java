package com.petertill.szigmav;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String GRAPHQL_ENDPOINT = "https://emma.mav.hu/otp2-backend/otp/routers/default/index/graphql";

    private MapView mapView;
    private ScheduledExecutorService executor;
    private Handler mainHandler;
    private OkHttpClient client;
    private JsonAdapter<VehicleResponse> vehicleResponseJsonAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // This sets the user agent for OSMDroid to avoid getting banned by tile servers
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);

        bottomNavigation.setSelectedItemId(R.id.pg_map);

        bottomNavigation.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.pg_map) {
                    // Handle reselection of pg_map
                } else if (itemId == R.id.pg_about) {
                    // Open a website
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/petertill/VonaTrack"));
                    MainActivity.this.startActivity(intent);
                }
                return false;
            }
        });

        bottomNavigation.setOnItemReselectedListener(new NavigationBarView.OnItemReselectedListener() {
            @Override
            public void onNavigationItemReselected(@NonNull MenuItem menuItem) {

            }
        });

        mapView = findViewById(R.id.map);
        mapView.setMultiTouchControls(true);

        mapView.setBuiltInZoomControls(false);  // Eltünteti a zoom gombokat
        mapView.setTileSource(TileSourceFactory.MAPNIK);

        GeoPoint hungaryCenter = new GeoPoint(47.1625, 19.5033);
        mapView.getController().setZoom(7);  // 7-es zoom a teljes országhoz jó közelítőleg
        mapView.getController().setCenter(hungaryCenter);

        BoundingBox hungaryBounds = new BoundingBox(
                48.6,   // north lat
                22.9,   // east lon
                45.7,   // south lat
                16.1    // west lon
        );

        mapView.setScrollableAreaLimitDouble(hungaryBounds);

        // Center map roughly to your area of interest
        IMapController mapController = mapView.getController();
        mapController.setZoom(7.0);
        mapController.setCenter(new GeoPoint(47.5, 19.0));

        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadScheduledExecutor();
        client = new OkHttpClient();

        Moshi moshi = new Moshi.Builder().build();
        vehicleResponseJsonAdapter = moshi.adapter(VehicleResponse.class);

        startPeriodicVehicleUpdates();
    }

    private void startPeriodicVehicleUpdates() {
        executor.scheduleWithFixedDelay(() -> {
            try {
                String queryJson = buildVehiclePositionsQuery();
                String response = postGraphQLQuery(GRAPHQL_ENDPOINT, queryJson);
                VehicleResponse vehicleResponse = vehicleResponseJsonAdapter.fromJson(response);

                if (vehicleResponse != null && vehicleResponse.data != null && vehicleResponse.data.vehiclePositions != null) {
                    mainHandler.post(() -> updateMapWithVehicles(vehicleResponse.data.vehiclePositions));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private void updateMapWithVehicles(List<VehicleResponse.VehiclePosition> vehicles) {
        mapView.getOverlays().clear();

        for (VehicleResponse.VehiclePosition vehicle : vehicles) {
            GeoPoint point = new GeoPoint(vehicle.lat, vehicle.lon);
            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setRotation((float) vehicle.heading);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            /*marker.setTitle(vehicle.label != null ? vehicle.label : "Vehicle");
            marker.setSnippet("Speed: " + vehicle.speed + " km/h\nHeading: " + vehicle.heading + "°");

            marker.setOnMarkerClickListener((m, mapView) -> {
                if (m.isInfoWindowShown()) {
                    m.closeInfoWindow();
                } else {
                    m.showInfoWindow();
                }
                return true;
            });

            int delay = getMaxDelay(vehicle);

            Drawable icon;
            if (delay <= 0) {
                icon = ContextCompat.getDrawable(this, R.drawable.azm_green);
            } else if (delay <= 300) {
                icon = ContextCompat.getDrawable(this, R.drawable.azm_yellow);
            } else {
                icon = ContextCompat.getDrawable(this, R.drawable.azm_red);
            }
            marker.setIcon(icon);*/
            StringBuilder snippet = new StringBuilder();
            snippet.append("Sebesség: ").append(vehicle.speed).append(" km/h\n");
            snippet.append("Irány: ").append(vehicle.heading).append("°\n");

            if (vehicle.trip != null) {
                snippet.append("Járat: ").append(vehicle.trip.tripShortName != null ? vehicle.trip.tripShortName : "N/A").append("\n");
                snippet.append("Úticél: ").append(vehicle.trip.tripHeadsign != null ? vehicle.trip.tripHeadsign : "N/A").append("\n");
                snippet.append("Vonat neve: ").append(vehicle.trip.trainName != null ? vehicle.trip.trainName : "N/A").append("\n");
                snippet.append("Kategória: ").append(vehicle.trip.trainCategoryName != null ? vehicle.trip.trainCategoryName : "N/A").append("\n");

                int delay = getMaxDelay(vehicle);
                snippet.append("Max késés: ").append(delay).append(" sec");

                Drawable icon;
                if (delay <= 0) {
                    icon = ContextCompat.getDrawable(this, R.drawable.azm_green);
                } else if (delay <= 300) {
                    icon = ContextCompat.getDrawable(this, R.drawable.azm_yellow);
                } else {
                    icon = ContextCompat.getDrawable(this, R.drawable.azm_red);
                }
                marker.setIcon(icon);
            }

            marker.setSnippet(snippet.toString());

            marker.setOnMarkerClickListener((m, mapView) -> {
                VehicleBottomSheet sheet = new VehicleBottomSheet(vehicle);
                sheet.show(((AppCompatActivity) this).getSupportFragmentManager(), "vehicle_info");
                return true;
            });

            mapView.getOverlays().add(marker);
        }

        mapView.invalidate();
    }

    private String buildVehiclePositionsQuery() {
        String query = """
      {
        vehiclePositions(
          swLat:45.5,
          swLon:16.1,
          neLat:48.7,
          neLon:22.8,
          modes:[RAIL, RAIL_REPLACEMENT_BUS]
        ) {
          trip {
            gtfsId
            tripShortName
            tripHeadsign
            trainName
            trainCategoryName
            stoptimes {
              arrivalDelay
              departureDelay
              realtimeArrival
              realtimeDeparture
              scheduledArrival
              scheduledDeparture
            }
          }
          vehicleId
          lat
          lon
          label
          speed
          heading
        }
      }
    """;
        return "{\"query\":\"" + query.replace("\"", "\\\"").replace("\n", "") + "\"}";
    }



    private String postGraphQLQuery(String url, String queryJson) throws IOException {
        RequestBody body = RequestBody.create(queryJson, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new IOException("Unexpected code " + response);
            return response.body().string();
        }
    }

    private int getMaxDelay(VehicleResponse.VehiclePosition vehicle) {
        if (vehicle.trip == null || vehicle.trip.stoptimes == null || vehicle.trip.stoptimes.isEmpty()) {
            return 0;
        }

        int maxDelay = 0;
        for (VehicleResponse.VehiclePosition.StopTime st : vehicle.trip.stoptimes) {
            maxDelay = Math.max(maxDelay, Math.max(st.arrivalDelay, st.departureDelay));
        }
        return maxDelay;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // --- JSON model classes for Moshi parsing ---
    public static class VehicleResponse {
        public Data data;

        public static class Data {
            public List<VehiclePosition> vehiclePositions;
        }

        public static class VehiclePosition {
            public Trip trip;
            public String vehicleId;
            public double lat;
            public double lon;
            public String label;
            public double speed;
            public double heading;

            public static class Trip {
                public String gtfsId;
                public String tripShortName;
                public String tripHeadsign;
                public String trainName;
                public String trainCategoryName;
                public List<StopTime> stoptimes;
            }

            public static class StopTime {
                public int arrivalDelay;   // Delay másodpercben
                public int departureDelay;
                public int realtimeArrival;
                public int realtimeDeparture;
                public int scheduledArrival;
                public int scheduledDeparture;
            }
        }

        public static class Trip {
            public String gtfsId;
            public String tripShortName;
            public String tripHeadsign;
        }
    }
}
