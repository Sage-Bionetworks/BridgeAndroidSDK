package org.sagebionetworks.bridge.researchstack.factory;

import android.support.annotation.NonNull;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.data.Archive;

/**
 * This class encapsulates creating Archive instances. This is needed to facilitate tests, because
 * once you create an Archive, you can't peek inside it to verify its properties.
 */
public class ArchiveFactory {
    /** Singleton instance. */
    public static final ArchiveFactory INSTANCE = new ArchiveFactory();

    /**
     * Private constructor, to enforce the singleton property. This prevents creating additional
     * instances, but the factory can still be mocked.
     */
    private ArchiveFactory() {
    }

    /**
     * Creates an archive for the given schema ID and the default revision 1.
     *
     * @param schemaId schema ID
     * @return archive builder with the given schema ID
     */
    @NonNull
    public Archive.Builder forActivity(@NonNull String schemaId) {
        return Archive.Builder.forActivity(schemaId);
    }

    /**
     * Creates an archive for the given schema ID and revision.
     *
     * @param schemaId       schema ID
     * @param schemaRevision schema revision
     * @return archive builder with the given schema ID and revision
     */
    @NonNull
    public Archive.Builder forActivity(@NonNull String schemaId, int schemaRevision) {
        return Archive.Builder.forActivity(schemaId, schemaRevision);
    }

    /**
     * Creates an archive for the given survey guid and createdOn timestamp. Important note: This is
     * the version timestamp of the survey as reported by the server, _not_ the timestamp of when
     * the participant responded to the survey.
     *
     * @param surveyGuid      survey guid
     * @param surveyCreatedOn survey createdOn
     * @return archive builder with the given survey guid and createdOn
     */
    @NonNull
    public Archive.Builder forSurvey(@NonNull String surveyGuid, @NonNull DateTime surveyCreatedOn) {
        return Archive.Builder.forSurvey(surveyGuid, surveyCreatedOn);
    }
}
