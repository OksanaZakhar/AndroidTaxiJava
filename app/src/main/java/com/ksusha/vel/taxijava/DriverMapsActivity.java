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
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.ksusha.vel.taxijava.databinding.ActivityDriverMapsBinding;

public class DriverMapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityDriverMapsBinding binding;


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

    FirebaseAuth auth;
    FirebaseUser currentUser;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        binding = ActivityDriverMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.singOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                auth.signOut();
                signOutDriver();

            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_driver);
        mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices
                .getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        buildLocationRequest();
        buildLocationCallBack();
        buildLocationSettingsRequest();

        startLocationUpdates();
    }

    private void signOutDriver() {

        String driverUserId = currentUser.getUid();
        DatabaseReference drivers = FirebaseDatabase.getInstance().getReference()
                .child(ChildDBFirebase.DRIVER.getTitle());

        GeoFire geoFire = new GeoFire(drivers);
        geoFire.removeLocation(driverUserId);

        Intent intent = new Intent(DriverMapsActivity.this,
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
                                        DriverMapsActivity.this,
                                        Manifest.permission.ACCESS_FINE_LOCATION) !=
                                        PackageManager.PERMISSION_GRANTED &&
                                        ActivityCompat
                                                .checkSelfPermission(
                                                        DriverMapsActivity.this,
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
                                            DriverMapsActivity.this,
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
                                Toast.makeText(DriverMapsActivity.this, message,
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
            LatLng driverLocation = new LatLng(currentLocation.getLatitude(),
                    currentLocation.getLongitude());

            mMap.moveCamera(CameraUpdateFactory.newLatLng(driverLocation));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(10));
            mMap.addMarker(new MarkerOptions().position(driverLocation).title("Driver Location"));

            String driverUserId = currentUser.getUid();
            DatabaseReference drivers = FirebaseDatabase.getInstance().getReference()
                    .child(ChildDBFirebase.DRIVER.getTitle());

            GeoFire geoFire = new GeoFire(drivers);
            geoFire.setLocation(driverUserId,
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
                                    DriverMapsActivity.this,
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
                    DriverMapsActivity.this,
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




