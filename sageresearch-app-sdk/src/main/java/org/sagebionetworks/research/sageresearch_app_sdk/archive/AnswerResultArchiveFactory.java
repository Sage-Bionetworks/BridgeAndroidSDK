package org.sagebionetworks.research.sageresearch_app_sdk.archive;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.bridge.data.JsonArchiveFile;
import org.sagebionetworks.research.domain.result.interfaces.AnswerResult;
import org.sagebionetworks.research.domain.result.interfaces.Result;

import javax.inject.Inject;

public class AnswerResultArchiveFactory implements AbstractResultArchiveFactory.ResultArchiveFactory {
    @Inject
    public AnswerResultArchiveFactory() {

    }

    @Override
    public boolean isSupported(@NonNull final Result result) {
        return result instanceof AnswerResult;
    }

    @NonNull
    @Override
    public ImmutableSet<? extends ArchiveFile> toArchiveFiles(@NonNull final Result result) {
        return ImmutableSet.of(
                new JsonArchiveFile(result.getIdentifier(),
                        new DateTime(result.getEndTime().toEpochMilli()),
                        ((AnswerResult) result).getAnswer()
                )
        );
    }
}
