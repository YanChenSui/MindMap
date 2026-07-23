package com.example.mindmap.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mindmap.MainActivity;
import com.example.mindmap.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class LoginActivity extends AppCompatActivity {
    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private CheckBox rememberLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LoginSession.isLoggedIn(this)) {
            openMainScreen();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        usernameLayout = findViewById(R.id.username_layout);
        passwordLayout = findViewById(R.id.password_layout);
        usernameInput = findViewById(R.id.username_input);
        passwordInput = findViewById(R.id.password_input);
        rememberLogin = findViewById(R.id.remember_login);
        MaterialButton loginButton = findViewById(R.id.login_button);

        loginButton.setOnClickListener(view -> attemptLogin());
        passwordInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void attemptLogin() {
        String username = LoginValidator.normalizeUsername(textOf(usernameInput));
        String password = textOf(passwordInput);
        usernameLayout.setError(null);
        passwordLayout.setError(null);

        boolean valid = true;
        if (!LoginValidator.isValidUsername(username)) {
            usernameLayout.setError(getString(R.string.login_username_error));
            valid = false;
        }
        if (!LoginValidator.isValidPassword(password)) {
            passwordLayout.setError(getString(
                    R.string.login_password_error,
                    LoginValidator.MIN_PASSWORD_LENGTH
            ));
            valid = false;
        }
        if (!valid) {
            return;
        }

        LoginSession.login(this, username, rememberLogin.isChecked());
        openMainScreen();
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private void openMainScreen() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
