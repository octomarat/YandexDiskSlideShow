package ru.yandex.slideshow;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorDescription;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainAuthActivity extends FragmentActivity {

    private final String LOG_TAG = "ExampleActivity";

    private final String CLIENT_ID = "e32c303e86224c7490c07142918d524b";
    private final String CLIENT_SECRET = "bdcdbe30415647fba6893a1aad371bcd";

    private final String ACCOUNT_TYPE = "com.yandex";
    private final String AUTH_URL = "https://oauth.yandex.ru/authorize?response_type=token&client_id=" + CLIENT_ID;
    private final String ACTION_ADD_ACCOUNT = "com.yandex.intent.ADD_ACCOUNT";
    private final int GET_ACCOUNT_CREDS_INTENT = 100;

    private final String KEY_CLIENT_SECRET = "client.secret";
    public static final String USERNAME_ENTRY = "username.entry";
    public static final String TOKEN_ENTRY = "token.entry";
    private final String USERNAME_VALUE = "";

    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_screen);
        tryLogin();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String token = preferences.getString(TOKEN_ENTRY, null);
        if (token == null) {
            getToken();
            return;
        }

        if (savedInstanceState == null) {
            startFileListFragment();
        }
    }

    private void startFileListFragment() {
        findViewById(R.id.splash_layout).setVisibility(View.GONE);
        getSupportFragmentManager()
        .beginTransaction()
        .replace(android.R.id.content, new FileListFragment(), FileListFragment.FRAGMENT_TAG)
        .commit();
    }

    private void tryLogin() {
        if (getIntent() != null && getIntent().getData() != null) {
            Uri data = getIntent().getData();
            setIntent(null);
            Pattern pattern = Pattern.compile("access_token=(.*?)(&|$)");
            Matcher matcher = pattern.matcher(data.toString());
            if (matcher.find()) {
                final String token = matcher.group(1);
                if (!TextUtils.isEmpty(token)) {
                    Log.d(LOG_TAG, "tryLogin: token: " + token);
                    saveToken(token);
                } else {
                    Log.w(LOG_TAG, "onRegistrationSuccess: empty token");
                }
            } else {
                Log.w(LOG_TAG, "onRegistrationSuccess: token not found in return url");
            }
        }
    }

    private void saveToken(String token) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(USERNAME_ENTRY, USERNAME_VALUE);
        editor.putString(TOKEN_ENTRY, token);
        editor.commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == GET_ACCOUNT_CREDS_INTENT) {
            if (resultCode == RESULT_OK) {
                Bundle bundle = data.getExtras();
                String name = bundle.getString(AccountManager.KEY_ACCOUNT_NAME);
                String type = bundle.getString(AccountManager.KEY_ACCOUNT_TYPE);
                Log.d(LOG_TAG, "GET_ACCOUNT_CREDS_INTENT: name=" + name + " type="+type);
                Account account = new Account(name, type);
                getAuthToken(account);
            }
        }
    }

    private void getAuthToken(Account account) {
        AccountManager systemAccountManager = AccountManager.get(getApplicationContext());
        Bundle options = new Bundle();
        options.putString(KEY_CLIENT_SECRET, CLIENT_SECRET);
        if(systemAccountManager != null) {
            systemAccountManager.getAuthToken(account, CLIENT_ID, options, this, new GetAuthTokenCallback(), null);
        }
    }

    private void getToken() {
        AccountManager accountManager = AccountManager.get(getApplicationContext());
        if(accountManager != null) {
            Account[] accounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
            Log.d(LOG_TAG, "accounts: " + (accounts != null ? accounts.length : null));
            if (accounts != null && accounts.length > 0) {
                // get the first account, for example (you must show the list and allow user to choose)
                Account account = accounts[0];
                Log.d(LOG_TAG, "account: " + account);
                getAuthToken(account);
                return;
            }
            Log.d(LOG_TAG, "No such accounts: " + ACCOUNT_TYPE);
            for (AuthenticatorDescription authDesc : accountManager.getAuthenticatorTypes()) {
                if (ACCOUNT_TYPE.equals(authDesc.type)) {
                    Log.d(LOG_TAG, "Starting " + ACTION_ADD_ACCOUNT);
                    Intent intent = new Intent(ACTION_ADD_ACCOUNT);
                    startActivityForResult(intent, GET_ACCOUNT_CREDS_INTENT);
                    return;
                }
            }
        }
        // no account manager for com.yandex
        new AuthDialogFragment().show(getSupportFragmentManager(), "No yandex accounts found");
    }

    private class GetAuthTokenCallback implements AccountManagerCallback<Bundle> {
        public void run(AccountManagerFuture<Bundle> result) {
            try {
                Bundle bundle = result.getResult();
                Log.d(LOG_TAG, "bundle: " + bundle);

                String message = (String) bundle.get(AccountManager.KEY_ERROR_MESSAGE);
                if (message != null) {
                    Toast.makeText(MainAuthActivity.this, message, Toast.LENGTH_LONG).show();
                }

                Intent intent = (Intent) bundle.get(AccountManager.KEY_INTENT);
                Log.d(LOG_TAG, "intent: " + intent);
                if (intent != null) {
                    // User input required
                    startActivityForResult(intent, GET_ACCOUNT_CREDS_INTENT);
                } else {
                    String token = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    Log.d(LOG_TAG, "GetAuthTokenCallback: token="+token);
                    saveToken(token);
                    startFileListFragment();
                }
            } catch (OperationCanceledException ex) {
                Log.d(LOG_TAG, "GetAuthTokenCallback", ex);
                Toast.makeText(MainAuthActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
            } catch (AuthenticatorException ex) {
                Log.d(LOG_TAG, "GetAuthTokenCallback", ex);
                Toast.makeText(MainAuthActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
            } catch (IOException ex) {
                Log.d(LOG_TAG, "GetAuthTokenCallback", ex);
                Toast.makeText(MainAuthActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    public class AuthDialogFragment extends DialogFragment {
        public AuthDialogFragment () {
            super();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle("Yandex account authentication")
                    .setMessage("Your account authentication required. Continue?")
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick (DialogInterface dialog, int which) {
                            dialog.dismiss();
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AUTH_URL)));
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick (DialogInterface dialog, int which) {
                            dialog.dismiss();
                            getActivity().finish();
                        }
                    })
                    .create();
        }
    }
}