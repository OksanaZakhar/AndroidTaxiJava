package com.ksusha.vel.taxijava;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.ksusha.vel.taxijava.databinding.ActivitySingInUserBinding;


public class UserSingInActivity extends AppCompatActivity {

    private FirebaseAuth auth;

    private String userIs = "";

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    ActivitySingInUserBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sing_in_user);

        binding = DataBindingUtil
                .setContentView(this, R.layout.activity_sing_in_user);

        binding.userSingInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                editor.putString("userIs", ChildDBFirebase.CLIENT.getTitle());
                editor.apply();
                userIs = ChildDBFirebase.CLIENT.getTitle();
                loginUser();
            }
        });

        binding.driverSingInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                editor.putString("userIs", ChildDBFirebase.DRIVER.getTitle());
                editor.apply();
                userIs = ChildDBFirebase.DRIVER.getTitle();
                loginUser();
            }
        });
    }


    @Override
    protected void onStart() {
        super.onStart();
        sharedPreferences = getSharedPreferences("sharedPreferences",
                Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        userIs = sharedPreferences.getString("userIs", "");

        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {

            if (userIs.equals(ChildDBFirebase.CLIENT.getTitle())) {
                startActivity(new Intent(this,
                        ClientMapsActivity.class));
            } else {
                if (userIs.equals(ChildDBFirebase.DRIVER.getTitle())) startActivity(new Intent(this,
                        DriverMapsActivity.class));
            }
        }
    }

    private boolean validateEmail() {

        String emailInput = binding.textInputEmail.getEditText().getText().toString()
                .trim();
        if (emailInput.isEmpty()) {
            binding.textInputEmail.setError("Please input your email");
            return false;
        } else {
            binding.textInputEmail.setError("");
            return true;
        }
    }

    private boolean validateName() {
        String nameInput = binding.textInputName.getEditText().getText().toString()
                .trim();
        if (nameInput.isEmpty()) {
            binding.textInputName.setError("Please input your name");
            return false;
        } else if (nameInput.length() > 15) {
            binding.textInputName.setError("Name length have to be less than 15");
            return false;
        } else {
            binding.textInputName.setError("");
            return true;
        }
    }

    private boolean validatePassword() {
        String passwordInput = binding.textInputPassword.getEditText().getText()
                .toString().trim();

        if (passwordInput.isEmpty()) {
            binding.textInputPassword.setError("Please input your password");
            return false;
        } else if (passwordInput.length() < 7) {
            binding.textInputPassword.setError("Password length have to be more than 6");
            return false;
        } else {
            binding.textInputPassword.setError("");
            return true;
        }
    }

    private boolean validateConfirmPassword() {
        String passwordInput = binding.textInputPassword.getEditText().getText()
                .toString().trim();
        String confirmPasswordInput = binding.textInputConfirmPassword.getEditText().getText()
                .toString().trim();
        if (!passwordInput.equals(confirmPasswordInput)) {
            binding.textInputPassword.setError("Passwords have to match");
            return false;
        } else {
            binding.textInputPassword.setError("");
            return true;
        }
    }

    public void loginUser() {
        if (!validateEmail() | !validateName() | !validatePassword() |
                !validateConfirmPassword()) {
            return;
        }
        auth.createUserWithEmailAndPassword(
                        binding.textInputEmail.getEditText().getText().toString().trim(),
                        binding.textInputPassword.getEditText().getText().toString().trim())
                .addOnCompleteListener(this,
                        new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    Log.d("TAG", "createUserWithEmail:success");
                                    FirebaseUser user = auth.getCurrentUser();
                                    if (userIs.equals(ChildDBFirebase.CLIENT.getTitle()))
                                        startActivity(new Intent(
                                                UserSingInActivity.this,
                                                ClientMapsActivity.class));
                                    if (userIs.equals(ChildDBFirebase.DRIVER.getTitle()))
                                        startActivity(new Intent(UserSingInActivity.this,
                                                DriverMapsActivity.class));
                                } else {
                                    Log.w("TAG", "createUserWithEmail:failure",
                                            task.getException());
                                    Toast.makeText(UserSingInActivity.this,
                                            "Authentication failed.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
    }

}
