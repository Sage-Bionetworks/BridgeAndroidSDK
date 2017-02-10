package org.sagebionetworks.bridge.android.manager.dao;

import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.sagebionetworks.bridge.android.BuildConfig;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by jyliu on 2/10/2017.
 */
@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class ConsentDAOTest {
    private ConsentDAO consentDAO;

    @Before
    public void setupTest() {
        consentDAO = new ConsentDAO(RuntimeEnvironment.application);
    }

    @Test
    public void sanityCheck() {

        assertTrue(consentDAO.listConsents().isEmpty());

        ConsentSignature retrievedConsent = consentDAO.getConsent("non-existent");

        assertNull(retrievedConsent);

        String subpopulation1 = "subpop1";
        ConsentSignature consent1 = new ConsentSignature().birthdate(LocalDate.now());

        consentDAO.putConsent(subpopulation1, consent1);

        retrievedConsent = consentDAO.getConsent(subpopulation1);

        assertEquals(consent1, retrievedConsent);
        assertTrue(consentDAO.listConsents().contains(subpopulation1));

        String subpopulation2 = "subpop2";
        ConsentSignature consent2 = new ConsentSignature().birthdate(LocalDate.now())
                .imageData("image2");

        consentDAO.putConsent(subpopulation2, consent2);

        retrievedConsent = consentDAO.getConsent(subpopulation2);

        assertEquals(consent2, retrievedConsent);
        assertEquals(2, consentDAO.listConsents().size());
        assertTrue(consentDAO.listConsents().containsAll(
                Arrays.asList(subpopulation1, subpopulation2)));

        consentDAO.remove(subpopulation1);

        retrievedConsent = consentDAO.getConsent(subpopulation1);
        assertNull(retrievedConsent);

        retrievedConsent = consentDAO.getConsent(subpopulation2);
        assertEquals(consent2, retrievedConsent);

        assertEquals(1, consentDAO.listConsents().size());
    }
}