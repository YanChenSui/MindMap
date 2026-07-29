package com.example.mindmap.profile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mindmap.MainActivity;
import com.example.mindmap.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class ProfileSetupActivity extends AppCompatActivity {
    public static final String EXTRA_EDIT_MODE = "edit_mode";

    private TextInputLayout accountNameLayout;
    private TextInputEditText accountNameInput;
    private Spinner genderSpinner;
    private Spinner ageGroupSpinner;
    private Spinner educationSpinner;
    private boolean editMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        editMode = getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false);
        if (!editMode && UserProfileSession.hasProfile(this)) {
            openMainScreen();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_setup);
        accountNameLayout = findViewById(R.id.account_name_layout);
        accountNameInput = findViewById(R.id.account_name_input);
        genderSpinner = findViewById(R.id.gender_spinner);
        ageGroupSpinner = findViewById(R.id.age_group_spinner);
        educationSpinner = findViewById(R.id.education_spinner);
        MaterialButton saveButton = findViewById(R.id.profile_save_button);

        configureSpinner(genderSpinner, UserProfileSession.GENDER_OPTIONS);
        configureSpinner(ageGroupSpinner, UserProfileSession.AGE_GROUP_OPTIONS);
        configureSpinner(educationSpinner, UserProfileSession.EDUCATION_OPTIONS);
        prefillExistingProfile();
        saveButton.setOnClickListener(view -> saveProfile());
    }

    private void configureSpinner(Spinner spinner, String[] options) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                options
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void prefillExistingProfile() {
        if (!UserProfileSession.hasProfile(this)) {
            return;
        }
        UserProfile profile = UserProfileSession.currentProfile(this);
        accountNameInput.setText(profile.accountName);
        genderSpinner.setSelection(UserProfileValidator.indexOf(UserProfileSession.GENDER_OPTIONS, profile.gender));
        ageGroupSpinner.setSelection(UserProfileValidator.indexOf(UserProfileSession.AGE_GROUP_OPTIONS, profile.ageGroup));
        educationSpinner.setSelection(UserProfileValidator.indexOf(UserProfileSession.EDUCATION_OPTIONS, profile.educationLevel));
    }

    private void saveProfile() {
        String accountName = accountNameInput.getText() == null ? "" : accountNameInput.getText().toString();
        UserProfile profile = new UserProfile(
                accountName,
                (String) genderSpinner.getSelectedItem(),
                (String) ageGroupSpinner.getSelectedItem(),
                (String) educationSpinner.getSelectedItem()
        );
        accountNameLayout.setError(null);
        if (!UserProfileValidator.isValidAccountName(accountName)) {
            accountNameLayout.setError(getString(R.string.profile_account_error));
            return;
        }
        try {
            UserProfileSession.saveProfile(this, profile);
            Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show();
            if (editMode) {
                finish();
            } else {
                openMainScreen();
            }
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openMainScreen() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
