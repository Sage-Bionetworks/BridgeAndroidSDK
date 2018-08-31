package org.sagebionetworks.research.sageresearch_app_sdk.archive;

import android.support.annotation.NonNull;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.data.JsonArchiveFile;
import org.sagebionetworks.research.domain.result.interfaces.Result;

import javax.inject.Inject;

public class BaseResultArchiveFactory implements AbstractResultArchiveFactory.ResultArchiveFactory {
    private static final ByteSource EMPTY_OBJECT = CharSource.wrap("{}").asByteSource(Charsets.UTF_8);

    @Inject
    public BaseResultArchiveFactory() {

    }

    @Override
    public boolean isSupported(@NonNull final Result result) {
        return result instanceof Result;
    }

    @NonNull
    @Override
    public ImmutableSet<JsonArchiveFile> toArchiveFiles(@NonNull final Result result) {
        return ImmutableSet.of(new JsonArchiveFile(
                result.getIdentifier(),
                new DateTime(result.getEndTime().toEpochMilli()),
                new Object()
        ));
    }
}
