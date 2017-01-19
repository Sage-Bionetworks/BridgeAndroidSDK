package org.sagebionetworks.bridge.sdk.restmm.model;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by TheMDP on 1/19/17.
 *
 * This class should be identical to ConsentSignatureBody, but with the difference in that
 * it stores birthdate as a string, that is based on birthdateFormat
 * This forces the date object format to be server dependent
 */

public class BridgeConsentSignatureBody {

    static final DateFormat birthdateFormat = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * The identifier for the study under which the user is signing in
     */
    public String study;

    /**
     * User's name
     */
    public String name;

    /**
     * User's birthdate
     */
    public String birthdate;

    /**
     * User's signature image data
     */
    public String imageData;

    /**
     * User's signature image mime type
     */
    public String imageMimeType;

    /**
     * User's sharing scope choice
     */
    public String scope;

    public BridgeConsentSignatureBody(
            String study,
            String name,
            Date birthdate,
            String imageData,
            String imageMimeType,
            String scope)
    {
        this.study = study;
        this.name = name;
        this.birthdate = birthdateFormat.format(birthdate);
        this.imageData = imageData;
        this.imageMimeType = imageMimeType;
        this.scope = scope;
    }
}
