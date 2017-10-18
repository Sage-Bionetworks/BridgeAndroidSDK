package org.sagebionetworks.bridge.android.authenticator;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import org.sagebionetworks.bridge.android.R;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.rx.RxHelper;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by jyliu on 8/28/2017.
 */

public class AuthenticatorActivity extends AccountAuthenticatorActivity {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatorActivity.class);

    private AuthenticationApi authenticationApi;
    private RxHelper rxHelper;

    @Override
    public void onCreate(Bundle saveInstanceState) {
        super.onCreate(saveInstanceState);
        setContentView(R.layout.osb_login);

        BridgeManagerProvider bridgeManagerProvider = BridgeManagerProvider.getInstance();
        this.authenticationApi = bridgeManagerProvider
                .getApiClientProvider()
                .getClient(AuthenticationApi.class);
        this.rxHelper = bridgeManagerProvider.getRxHelper();

        findViewById(R.id.submit).setOnClickListener(v -> submit());
    }

    private void submit() {
        final String study = BridgeManagerProvider.getInstance().getBridgeConfig().getStudyId();
        final String email = ((TextView) findViewById(R.id.email)).getText().toString();
        final String password = ((TextView) findViewById(R.id.password)).getText().toString();

        final String accountType = getIntent().getStringExtra(Authenticator.Keys.ACCOUNT_TYPE);
        final String tokenType = getIntent().getStringExtra(Authenticator.Keys.TOKEN_TYPE);
        rxHelper.toBodySingle(authenticationApi.signIn(
                new SignIn()
                        .email(email)
                        .password(password)
                        .study(study)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(userSessionInfo -> {
                    Account account = new Account(email, accountType);
                    AccountManager am = AccountManager.get(getBaseContext());
                    am.addAccountExplicitly(account, password, null);
                    am.setAuthToken(account, tokenType, userSessionInfo.getSessionToken());

                    final Bundle b = new Bundle();
                    b.putString(AccountManager.KEY_ACCOUNT_NAME, email);
                    b.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType);
                    b.putString(AccountManager.KEY_AUTHTOKEN, userSessionInfo.getSessionToken());

                }, throwable -> {
                    LOGGER.info("signIn failed", throwable);
                    Toast.makeText(getBaseContext(), throwable.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
