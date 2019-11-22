package org.sagebionetworks.bridge.android.manager.dao;

import static com.google.common.base.Preconditions.checkNotNull;

import android.content.Context;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.collect.Sets;

import org.sagebionetworks.bridge.android.di.BridgeStudyScope;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import javax.inject.Inject;

/**
 * Created by jyliu on 2/8/2017.
 */
@AnyThread
@BridgeStudyScope // TODO: consider scoping to @BridgeStudyParticipantScope @liujoshua 2018/10/09
public class ConsentDAO extends SharedPreferencesJsonDAO {
    private static final Logger logger = LoggerFactory.getLogger(ConsentDAO.class);

    private static final String PREFERENCES_FILE = "consents";

    // in case we store additional consent objects, let's "namespace" the subpopulation key
    private static final String CONSENT_KEY_PREFIX = "subpopulation-";

    @Inject
    public ConsentDAO(Context applicationContext) {
        super(applicationContext, PREFERENCES_FILE);
    }

    /**
     * Removes all saved data
     */
    public void clear() {
        sharedPreferences.edit().clear().commit();
    }

    /**
     * This returns the subpouplationGuid of consent signatures stored locally. It is not to be used
     * as a means to determine the participant's consent status. For the participant's consent
     * state, use ConsentManager (preferred) or UserSessionInfo
     *
     * @return subpopulationGuids
     */
    @NonNull
    public Set<String> listConsents() {
        Set<String> subpopulations = Sets.newHashSet();

        for (String key : sharedPreferences.getAll().keySet()) {
            if (key.startsWith(CONSENT_KEY_PREFIX)) {
                String subpopulation = key.substring(CONSENT_KEY_PREFIX.length());
                subpopulations.add(subpopulation);
            }
        }

        logger.debug("listConsents called, found subpopulations: " + subpopulations);

        return subpopulations;
    }

    @Nullable
    public ConsentSignature getConsent(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        ConsentSignature consentSignature = getValue(
                consentKey(subpopulationGuid),
                ConsentSignature.class);

        logger.debug("getConsent called for subpopulation " + subpopulationGuid
                + ", found: " + consentSignature);

        return consentSignature;
    }

    public void putConsent(@NonNull String subpopulationGuid,
                           @NonNull ConsentSignature consentSignature) {
        checkNotNull(subpopulationGuid);
        checkNotNull(consentSignature);

        logger.debug("putConsent called for subpopulations " + subpopulationGuid
                + ", with: " + consentSignature);

        setValue(consentKey(subpopulationGuid), consentSignature, ConsentSignature.class);
    }

    public void removeConsent(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        removeValue(consentKey(subpopulationGuid));
    }

    private String consentKey(String subpopulationGuid) {
        return CONSENT_KEY_PREFIX + subpopulationGuid;
    }
}
