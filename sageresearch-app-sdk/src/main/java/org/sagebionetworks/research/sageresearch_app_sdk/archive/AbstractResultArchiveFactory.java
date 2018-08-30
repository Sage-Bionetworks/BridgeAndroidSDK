package org.sagebionetworks.research.sageresearch_app_sdk.archive;

import android.support.annotation.NonNull;

import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.research.domain.result.interfaces.Result;

public interface AbstractResultArchiveFactory {
    interface ResultArchiveFactory {
        boolean isSupported(@NonNull Result result);

        @NonNull
        ImmutableSet<? extends ArchiveFile> toArchiveFiles(@NonNull Result result);
    }

    @NonNull
    ImmutableSet<? extends ArchiveFile> toArchiveFiles(@NonNull Result result);
}
