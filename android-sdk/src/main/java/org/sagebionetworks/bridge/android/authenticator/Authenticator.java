package org.sagebionetworks.bridge.android.authenticator;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.common.base.Strings;

import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.rx.RxHelper;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;


/*
 * Implement AbstractAccountAuthenticator and stub out all
 * of its methods
 */
public class Authenticator extends AbstractAccountAuthenticator {
    private static final Logger LOGGER = LoggerFactory.getLogger(Authenticator.class);

    public static final class Keys {
        public static final String ACCOUNT_TYPE = "ACCOUNT_TYPE";
        public static final String TOKEN_TYPE = "TOKEN_TYPE";
        public static final String ACCOUNT_EMAIL = "ACCOUNT_EMAIL";
        public static final String ACCOUNT_PASSWORD = "ACCOUNT_PASSWORD";
        public static final String IS_ADDING_ACCOUNT = "IS_ADDING_ACCOUNT";
    }

    public static final String AUTHTOKEN_TYPE_BRIDGE_SESSION = "BRIDGE_SESSION";

    private final AuthenticationApi authenticationApi;
    private final Context context;
    private final String studyId;
    private final AccountManager am;
    private final RxHelper rxHelper;
    private final String accountType;
    private final String authTokenType;

    public Authenticator(Context context) {
        super(context);

        this.context = context;
        this.am = AccountManager.get(context);

        BridgeManagerProvider bridgeManagerProvider = BridgeManagerProvider.getInstance();

        this.studyId = bridgeManagerProvider.getBridgeConfig().getStudyId();
        this.rxHelper = bridgeManagerProvider.getRxHelper();

        this.accountType = bridgeManagerProvider.getBridgeConfig().getAccountType();
        this.authTokenType = bridgeManagerProvider.getBridgeConfig().getAuthTokenType();

        this.authenticationApi = BridgeManagerProvider.getInstance()
                .getApiClientProvider()
                .getClient(AuthenticationApi.class);
    }

    // Editing properties is not supported
    @Override
    public Bundle editProperties(
            AccountAuthenticatorResponse response, String accountType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle addAccount(
            AccountAuthenticatorResponse response,
            String accountType,
            String authTokenType,
            String[] requiredFeature,
            Bundle options) throws NetworkErrorException {
        checkArgument(this.accountType.equals(accountType), "unknown accountType: %s", accountType);
        checkArgument(this.authTokenType.equals(authTokenType), "unknown authTokenType: %s", authTokenType);

        String email = options.getString(Keys.ACCOUNT_EMAIL);
        String password = options.getString(Keys.ACCOUNT_PASSWORD);

        if (!Strings.isNullOrEmpty(email) & !Strings.isNullOrEmpty(password)) {
            LOGGER.debug("Signing in via email and password");
            // We have all the info to add ana ccount. Make network call and notify of response
            rxHelper.toBodySingle(authenticationApi.signIn(
                    new SignIn()
                            .email(email)
                            .password(password)
                            .study(studyId)
            )).subscribe(
                    userSessionInfo -> {
                        LOGGER.debug("Successfully logged in");
                        Account account = new Account(email, accountType);
                        am.addAccountExplicitly(account, password, null);
                        am.setAuthToken(account, authTokenType, userSessionInfo.getSessionToken());
                        response.onResult(addAccountSuccess(email, accountType, null));
                    }, throwable -> {
                        LOGGER.debug("Failed to log in", throwable);
                        response.onError(0, throwable.getMessage());
                    });
            return null;
        }

        // Specify activity for adding account
        final Intent intent = new Intent(context, AuthenticatorActivity.class)
                .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
                .putExtra(Keys.ACCOUNT_TYPE, accountType)
                .putExtra(Keys.TOKEN_TYPE, authTokenType)

                .putExtra(Keys.IS_ADDING_ACCOUNT, true);

        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    private Bundle addAccountSuccess(String accountName, String accountType, String authToken) {
        final Bundle b = new Bundle();
        b.putString(AccountManager.KEY_ACCOUNT_NAME, accountName);
        b.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType);
        b.putString(AccountManager.KEY_AUTHTOKEN, authToken);
        return b;
    }

    private Bundle addAccountFail(int errorCode, String errorMessage) {
        final Bundle b = new Bundle();
        b.putInt(AccountManager.KEY_ERROR_CODE, errorCode);
        b.putString(AccountManager.KEY_ERROR_MESSAGE, errorMessage);
        return b;
    }

    @Override
    public Bundle confirmCredentials(
            AccountAuthenticatorResponse response,
            Account account,
            Bundle options) throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
        LOGGER.debug("getAuthToken");

        if (!this.authTokenType.equals(authTokenType)) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ERROR_MESSAGE, "invalid authTokenType");
            return result;
        }

        final AccountManager accountManager = AccountManager.get(context);


        // check if token is in AccountManager cache
        String authToken = accountManager.peekAuthToken(account, authTokenType);

        LOGGER.debug("peekAuthToken returned: " + authToken);

        if (Strings.isNullOrEmpty(authToken)) {
            final String password = accountManager.getPassword(account);
            if (password != null) {
                try {
                    authToken = authenticationApi.signIn(new SignIn()
                            .email(account.name)
                            .password(password)
                            .study(studyId)).execute().body().getSessionToken();
                } catch (Throwable t) {
                    if (t instanceof ConsentRequiredException) {
                        authToken = ((ConsentRequiredException) t).getSession().getSessionToken();
                    } else if (!(t instanceof EntityNotFoundException)) {
                        throw new NetworkErrorException(t);
                    }
                }
            }
        }

        if (!Strings.isNullOrEmpty(authToken)) {
            return addAccountSuccess(account.name, account.type, authToken);
        }

        // If we get here, then we couldn't access the user's password - so we
        // need to re-prompt them for their credentials. We do that by creating
        // an intent to display our AuthenticatorActivity.
        final Intent intent = new Intent(context, AuthenticatorActivity.class)
                .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
                .putExtra(Keys.ACCOUNT_TYPE, account.type)
                .putExtra(Keys.TOKEN_TYPE, authTokenType)

                .putExtra(Keys.ACCOUNT_EMAIL, account.name);

        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    // Getting a label for the auth token is not supported
    @Override
    public String getAuthTokenLabel(String s) {
        throw new UnsupportedOperationException();
    }

    // Updating user credentials is not supported
    @Override
    public Bundle updateCredentials(
            AccountAuthenticatorResponse r,
            Account account,
            String s, Bundle bundle) throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }

    // Checking features for the account is not supported
    @Override
    public Bundle hasFeatures(
            AccountAuthenticatorResponse r,
            Account account, String[] strings) throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }

}