package com.techm.fingerprintauthenticator.ui.login;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.techm.fingerprintauthenticator.R;
import com.techm.fingerprintauthenticator.data.Utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.Executor;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class LoginActivity extends AppCompatActivity {

    private static final String KEY_NAME = "AramcoPassword";
    private LoginViewModel loginViewModel;
    private BiometricPrompt.PromptInfo promptInfo;
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private int AuthFailCount = 0;
    private EditText passwordEditText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        loginViewModel = ViewModelProviders.of(this, new LoginViewModelFactory())
                .get(LoginViewModel.class);

        final EditText usernameEditText = findViewById(R.id.username);
        passwordEditText = findViewById(R.id.password);
        final Button loginButton = findViewById(R.id.login);
        final ProgressBar loadingProgressBar = findViewById(R.id.loading);

        loginViewModel.getLoginFormState().observe(this, new Observer<LoginFormState>() {
            @Override
            public void onChanged(@Nullable LoginFormState loginFormState) {
                if (loginFormState == null) {
                    return;
                }
                loginButton.setEnabled(loginFormState.isDataValid());
                if (loginFormState.getUsernameError() != null) {
                    usernameEditText.setError(getString(loginFormState.getUsernameError()));
                }
                if (loginFormState.getPasswordError() != null) {
                    passwordEditText.setError(getString(loginFormState.getPasswordError()));
                }
            }
        });

        loginViewModel.getLoginResult().observe(this, new Observer<LoginResult>() {
            @Override
            public void onChanged(@Nullable LoginResult loginResult) {
                if (loginResult == null) {
                    return;
                }
                loadingProgressBar.setVisibility(View.GONE);
                if (loginResult.getError() != null) {
                    showLoginFailed(loginResult.getError());
                }
                if (loginResult.getSuccess() != null) {
                    //updateUiWithUser(loginResult.getSuccess());
                }
                setResult(Activity.RESULT_OK);

                //Complete and destroy login activity once successful
                finish();
                gotoHomeScreen();
            }
        });

        TextWatcher afterTextChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // ignore
            }

            @Override
            public void afterTextChanged(Editable s) {
                loginViewModel.loginDataChanged(usernameEditText.getText().toString(),
                        passwordEditText.getText().toString());
            }
        };
        usernameEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    loginViewModel.login(usernameEditText.getText().toString(),
                            passwordEditText.getText().toString());
                }
                return false;
            }
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePassword();
                loadingProgressBar.setVisibility(View.VISIBLE);
                loginViewModel.login(usernameEditText.getText().toString(),
                        passwordEditText.getText().toString());
            }
        });

        if (isPasswordStored()) {
            checkBiometricSettings();
        }
    }

    private void gotoHomeScreen() {
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        startActivity(intent);
    }

    private boolean isPasswordStored() {
        return Utils.isPasswordStored(this);
    }

    private void checkBiometricSettings() {
        BiometricManager biometricManager = BiometricManager.from(this);
        switch (biometricManager.canAuthenticate()) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                Log.d("MY_APP_TAG", "App can authenticate using biometrics.");
                showBiometricDialog();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                Log.e("MY_APP_TAG", "No biometric features available on this device.");
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                Log.e("MY_APP_TAG", "Biometric features are currently unavailable.");
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                Log.e("MY_APP_TAG", "The user hasn't associated " +
                        "any biometric credentials with their account.");
                break;
        }
    }

    private void showBiometricDialog() {
        promptInfo = createBiometricDialog();
        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(LoginActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                        "Authentication error: " + errString, Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(getApplicationContext(),
                        "Authentication succeeded!", Toast.LENGTH_SHORT).show();
                retrievePassword();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed",
                        Toast.LENGTH_SHORT)
                        .show();
                vibrate();
                AuthFailCount++;
                if (AuthFailCount <= 3) {
                    biometricPrompt.authenticate(promptInfo);
                }
            }
        });
        biometricPrompt.authenticate(promptInfo);
    }

    private void retrievePassword() {
        Log.d("MY_APP_TAG", "retrievePassword()");
        String encodedEncryptedPwd = Utils.getEncryptedPwd(LoginActivity.this);
        Log.d("MY_APP_TAG", "encodedEncryptedPwd: "+encodedEncryptedPwd);
        String encodedEncryptedIV = Utils.getEncryptedIV(LoginActivity.this);
        Log.d("MY_APP_TAG", "encodedEncryptedIV: "+encodedEncryptedIV);
        if(getSecretKey()==null){
            boolean isKeyGenerated = generateSecretKey(new KeyGenParameterSpec.Builder(
                    KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(false)
                    .build());
            if (!isKeyGenerated)
                return;
        }

        Cipher cipher = getCipher();
        SecretKey secretKey = getSecretKey();
        try {
            byte[] decodedEncryptionIVbytes = Base64.decode(encodedEncryptedIV, Base64.DEFAULT);
            Log.d("MY_APP_TAG", "decodedEncryptionIVbytes: "+decodedEncryptionIVbytes);
            byte[] decodedEncryptedPwdBytes = Base64.decode(encodedEncryptedPwd, Base64.DEFAULT);
            Log.d("MY_APP_TAG", "decodedEncryptedPwdBytes: "+decodedEncryptedPwdBytes);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(decodedEncryptionIVbytes));
            byte[] decryptedPwdBytes = cipher.doFinal(decodedEncryptedPwdBytes);
            String password = new String(decryptedPwdBytes, Charset.defaultCharset());
            Toast.makeText(LoginActivity.this, "password: " + password, Toast.LENGTH_LONG).show();
            finish();
            gotoHomeScreen();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
    }

    private void savePassword() {
        String pwdStr = passwordEditText.getText().toString();
        boolean isKeyGenerated = generateSecretKey(new KeyGenParameterSpec.Builder(
                KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(false)
                .build());
        if (!isKeyGenerated)
            return;
        Cipher cipher = getCipher();
        SecretKey secretKey = getSecretKey();
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            String encodedEncryptionIV = Base64.encodeToString(cipher.getIV(), Base64.DEFAULT);
            Log.d("MY_APP_TAG", "encodedEncryptionIV: "+encodedEncryptionIV);
            byte[] encryptedPwdBytes = cipher.doFinal(
                    pwdStr.getBytes(Charset.defaultCharset()));
            String encodedEncryptedPwdStr = Base64.encodeToString(encryptedPwdBytes, Base64.DEFAULT);
            Log.d("MY_APP_TAG", "encodedEncryptedPwdStr: "+encodedEncryptedPwdStr);
            Toast.makeText(LoginActivity.this, "password saved", Toast.LENGTH_LONG).show();
            Utils.savePassword(LoginActivity.this, encodedEncryptedPwdStr);
            Utils.saveEncrytionIV(LoginActivity.this, encodedEncryptionIV);
            Utils.updatePasswordInfo(LoginActivity.this, true);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
    }

    private boolean generateSecretKey(KeyGenParameterSpec keyGenParameterSpec) {
        KeyGenerator keyGenerator = null;
        try {
            keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
            return false;
        }
        try {
            keyGenerator.init(keyGenParameterSpec);
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
            return false;
        }
        keyGenerator.generateKey();
        return true;
    }

    private SecretKey getSecretKey() {
        KeyStore keyStore = null;
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            e.printStackTrace();
            return null;
        }

        // Before the keystore can be accessed, it must be loaded.
        try {
            keyStore.load(null);
        } catch (CertificateException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        try {
            return ((SecretKey) keyStore.getKey(KEY_NAME, null));
        } catch (KeyStoreException e) {
            e.printStackTrace();
            return null;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Cipher getCipher() {
        try {
            return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            return null;
        }
    }


    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
// Vibrate for 500 milliseconds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            v.vibrate(500);
        }
    }

    private BiometricPrompt.PromptInfo createBiometricDialog() {
        return new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login")
                .setSubtitle("Login using your biometric credentials")
                // Authenticate without requiring the user to press a "confirm"
                // button after satisfying the biometric check
                .setConfirmationRequired(false)
                .setNegativeButtonText("Cancel")
                .build();
    }

    private void updateUiWithUser(LoggedInUserView model) {
        String welcome = getString(R.string.welcome) + model.getDisplayName();
        // TODO : initiate successful logged in experience
        Toast.makeText(getApplicationContext(), welcome, Toast.LENGTH_LONG).show();
    }

    private void showLoginFailed(@StringRes Integer errorString) {
        Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
    }
}