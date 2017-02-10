package org.sagebionetworks.bridge.android.manager.dao;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.collect.Maps;
import com.google.gson.reflect.TypeToken;

import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jyliu on 2/8/2017.
 */
public class ConsentDAO extends SharedPreferencesJsonDAO {
    private static final Logger logger = LoggerFactory.getLogger(ConsentDAO.class);

    private static final String PREFRENCES_FILE = "consents";
    private static final String KEY_CONSENT_MAP = "consent-map";

    private static final TypeToken<Map<String, ConsentSignature>> CONSENT_MAP_TYPE =
            new TypeToken<Map<String, ConsentSignature>>() {
            };


    public ConsentDAO(Context applicationContext) {
        super(applicationContext, PREFRENCES_FILE);
    }

    @NonNull
    public Set<String> list() {
        Set<String> subpopulations = load().keySet();

        logger.debug("list called, found subpopulations: " + subpopulations);

        return subpopulations;
    }

    @Nullable
    public ConsentSignature get(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        Map<String, ConsentSignature> consents = load();
        ConsentSignature consentSignature = consents.get(subpopulationGuid);

        logger.debug("get called for subpopulation " + subpopulationGuid
                + ", found: " + consentSignature);

        return consentSignature;
    }

    public synchronized void put(@NonNull String subpopulationGuid,
                                 @NonNull ConsentSignature consentSignature) {
        checkNotNull(subpopulationGuid);
        checkNotNull(consentSignature);

        logger.debug("put called for subpopulations " + subpopulationGuid
                + ", with: " + consentSignature);

        Map<String, ConsentSignature> consents = load();
        consents.put(subpopulationGuid, consentSignature);
        persist(consents);
    }

    public synchronized void remove(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        Map<String, ConsentSignature> consents = load();
        ConsentSignature consent = consents.remove(subpopulationGuid);

        logger.debug("remove called for subpopulations " + subpopulationGuid
                + ", removed: " + consent);

        persist(consents);
    }

    private synchronized Map<String, ConsentSignature> load() {
        Map<String, ConsentSignature> map = getValue(KEY_CONSENT_MAP, CONSENT_MAP_TYPE);
        if (map == null) {
            map = Maps.newHashMap();
        }
        return map;
    }

    private synchronized void persist(Map<String, ConsentSignature> consentMap) {
        setValue(KEY_CONSENT_MAP, consentMap, CONSENT_MAP_TYPE);
    }
}
