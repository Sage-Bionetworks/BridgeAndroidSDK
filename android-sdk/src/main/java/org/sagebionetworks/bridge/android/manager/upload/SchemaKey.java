package org.sagebionetworks.bridge.android.manager.upload;

/** Encapsulates schema ID and revision. */
public class SchemaKey {
    private final String id;
    private final int revision;

    public SchemaKey(String id, int revision) {
        this.id = id;
        this.revision = revision;
    }

    public String getId() {
        return id;
    }

    public int getRevision() {
        return revision;
    }
}
