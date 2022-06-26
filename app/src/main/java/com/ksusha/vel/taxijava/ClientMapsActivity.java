package com.ksusha.vel.taxijava;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ksusha.vel.taxijava.databinding.ActivityClientMapsBinding;

import java.util.List;

public class ClientMapsActivity extends FragmentActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private ActivityClientMapsBinding binding;


    private static final int CHECK_SETTINGS_CODE = 111;
    private static final int REQUEST_LOCATION_PERMISSION = 222;

    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private LocationCallback locationCallback;
    private Location currentLocation;

    private boolean isLocationUpdatesActive;
    private Context context;


    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    private DatabaseReference drivers;
    private DatabaseReference driverLocation;
    private Marker driverMarker;
    private int searchRadius = 1;
    private Boolean isDriverFound = false;
    private String nearestDriverId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        binding = ActivityClientMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.singOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                auth.signOut();
                signOutClient();

            }
        });

        drivers = FirebaseDatabase.getInstance().getReference()
                .child(ChildDBFirebase.DRIVER.getTitle());

        binding.orderTaxi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.orderTaxi.setText("Search taxi");
                searchTaxi();
            }
        });


        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_client);
        mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices
                .getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        buildLocationRequest();
        buildLocationCallBack();
        buildLocationSettingsRequest();

        startLocationUpdates();
    }

    private void searchTaxi() {
        GeoFire geoFire = new GeoFire(drivers);
        GeoQuery geoQuery = geoFire.queryAtLocation(
                new GeoLocation(currentLocation.getLatitude(), currentLocation.getLongitude()),
                searchRadius);

        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!isDriverFound) {
                    isDriverFound = true;
                    nearestDriverId = key;
                    getLocationDriver();
                }
            }

            @Override
            public void onKeyExited(String key) {
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
            }

            @Override
            public void onGeoQueryReady() {
                if (!isDriverFound) {
                    searchRadius++;
                    searchTaxi();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
            }
        });
    }

    private void getLocationDriver() {
        driverLocation = FirebaseDatabase.getInstance().getReference()
                .child(ChildDBFirebase.DRIVER.getTitle())
                .child(nearestDriverId)
                .child("l");

        driverLocation.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    List<Object> driverCoordinates = (List<Object>) snapshot.getValue();
                    double latitude = 0;
                    double longitude = 0;
                    if (driverCoordinates.get(0) != null) {
                        latitude = Double.parseDouble(driverCoordinates.get(0).toString());
                    }
                    if (driverCoordinates.get(1) != null) {
                        longitude = Double.parseDouble(driverCoordinates.get(1).toString());
                    }

                    LatLng driverLatLng = new LatLng(latitude, longitude);

                    if (driverMarker != null) {
                        driverMarker.remove();
                    }

                    Location driverLocation = new Location("");
                    driverLocation.setLatitude(latitude);
                    driverLocation.setLongitude(longitude);
                    float distanceToDriver = driverLocation.distanceTo(currentLocation) / 1000;
                    binding.orderTaxi.setTextSize(14);
                    binding.orderTaxi.setText("Distance to driver " + String.format("%.2f", distanceToDriver) + "km");

                    mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Driver Location"));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }


    private void signOutClient() {

        String clientUserId = currentUser.getUid();
        DatabaseReference client = FirebaseDatabase.getInstance().getReference()
                .child(ChildDBFirebase.CLIENT.getTitle());

        GeoFire geoFire = new GeoFire(client);
        geoFire.removeLocation(clientUserId);

        Intent intent = new Intent(ClientMapsActivity.this,
                UserSingInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        updateLocationUi();
    }

    private void buildLocationRequest() {

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);

    }

    private void buildLocationCallBack() {

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                currentLocation = locationResult.getLastLocation();

                updateLocationUi();
            }
        };
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder =
                new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();
    }

    private void startLocationUpdates() {

        isLocationUpdatesActive = true;

        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(this,
                        new OnSuccessListener<LocationSettingsResponse>() {
                            @Override
                            public void onSuccess(
                                    LocationSettingsResponse locationSettingsResponse) {

                                if (ActivityCompat.checkSelfPermission(
                                        ClientMapsActivity.this,
                                        Manifest.permission.ACCESS_FINE_LOCATION) !=
                                        PackageManager.PERMISSION_GRANTED &&
                                        ActivityCompat
                                                .checkSelfPermission(
                                                        ClientMapsActivity.this,
                                                        Manifest.permission
                                                                .ACCESS_COARSE_LOCATION) !=
                                                PackageManager.PERMISSION_GRANTED) {
                                    return;
                                }
                                fusedLocationClient.requestLocationUpdates(
                                        locationRequest,
                                        locationCallback,
                                        Looper.myLooper()
                                );

                                updateLocationUi();
                            }
                        })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {

                        int statusCode = ((ApiException) e).getStatusCode();

                        switch (statusCode) {

                            case LocationSettingsStatusCodes
                                    .RESOLUTION_REQUIRED:
                                try {
                                    ResolvableApiException resolvableApiException =
                                            (ResolvableApiException) e;
                                    resolvableApiException.startResolutionForResult(
                                            ClientMapsActivity.this,
                                            CHECK_SETTINGS_CODE
                                    );
                                } catch (IntentSender.SendIntentException sie) {
                                    sie.printStackTrace();
                                }
                                break;
                            case LocationSettingsStatusCodes
                                    .SETTINGS_CHANGE_UNAVAILABLE:
                                String message =
                                        "Adjust location settings on your device";
                                Toast.makeText(ClientMapsActivity.this, message,
                                        Toast.LENGTH_LONG).show();

                                isLocationUpdatesActive = false;
                        }
                        updateLocationUi();
                    }
                });
    }


    private void stopLocationUpdates() {

        if (!isLocationUpdatesActive) {
            return;
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        isLocationUpdatesActive = false;
                    }
                });
    }

    private void updateLocationUi() {

        if (currentLocation != null) {
            LatLng clientLocation = new LatLng(currentLocation.getLatitude(),
                    currentLocation.getLongitude());

            mMap.moveCamera(CameraUpdateFactory.newLatLng(clientLocation));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(10));
            mMap.addMarker(new MarkerOptions().position(clientLocation).title("Client Location"));

            String clientUserId = currentUser.getUid();
            DatabaseReference clients = FirebaseDatabase.getInstance().getReference()
                    .child(ChildDBFirebase.CLIENT.getTitle());


            GeoFire geoFire = new GeoFire(clients);
            geoFire.setLocation(clientUserId,
                    new GeoLocation(currentLocation.getLatitude(), currentLocation.getLongitude()));

        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {

            case CHECK_SETTINGS_CODE:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.d("MainActivity", "User has agreed to change location" +
                                "settings");
                        startLocationUpdates();
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.d("MainActivity", "User has not agreed to change location" +
                                "settings");
                        isLocationUpdatesActive = false;
                        updateLocationUi();
                        break;
                }
                break;
        }
    }


    private void requestLocationPermission() {

        boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        );

        if (shouldProvideRationale) {

            showSnackBar(
                    "Location permission is needed for " +
                            "app functionality",
                    "OK",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ActivityCompat.requestPermissions(
                                    ClientMapsActivity.this,
                                    new String[]{
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                    },
                                    REQUEST_LOCATION_PERMISSION
                            );
                        }
                    }
            );
        } else {
            ActivityCompat.requestPermissions(
                    ClientMapsActivity.this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSION
            );
        }
    }

    private void showSnackBar(
            final String mainText,
            final String action,
            View.OnClickListener listener) {

        Snackbar.make(
                        findViewById(android.R.id.content),
                        mainText,
                        Snackbar.LENGTH_INDEFINITE)
                .setAction(
                        action,
                        listener
                )
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {

            if (grantResults.length <= 0) {
                Log.d("onRequestPermissions",
                        "Request was cancelled");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isLocationUpdatesActive) {
                    startLocationUpdates();
                }
            } else {
                showSnackBar(
                        "Turn on location on settings",
                        "Settings",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent intent = new Intent();
                                intent.setAction(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts(
                                        "package",
                                        context.getPackageName(),
                                        null
                                );
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        }
                );
            }
        }
    }

    private boolean checkLocationPermission() {

        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isLocationUpdatesActive && checkLocationPermission()) {
            startLocationUpdates();
        } else if (!checkLocationPermission()) {
            requestLocationPermission();
        }
    }

}




