package org.sagebionetworks.research.sageresearch_app_sdk.archive;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.data.ByteSourceArchiveFile;
import org.sagebionetworks.research.domain.result.interfaces.FileResult;
import org.sagebionetworks.research.domain.result.interfaces.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.bp.Instant;

import java.io.File;

import javax.inject.Inject;

public class FileResultArchiveFactory implements AbstractResultArchiveFactory.ResultArchiveFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileResultArchiveFactory.class);

    @Inject
    public FileResultArchiveFactory() {
    }

    @Override
    public boolean isSupported(@NonNull final Result result) {
        return result instanceof FileResult;
    }

    @NonNull
    @Override
    public ImmutableSet<ByteSourceArchiveFile> toArchiveFiles(@NonNull final Result result) {
        FileResult fileResult = (FileResult) checkNotNull(result);

        if (fileResult.getRelativePath() == null) {
            LOGGER.warn("Null relative path, skipping file result: {}", fileResult);
            return ImmutableSet.of();
        }
        File file = new File(fileResult.getRelativePath());
        if (!file.isFile()) {
            LOGGER.warn("No file found at relative path, skipping file result: {}", fileResult);
        }
        Instant endTime = result.getEndTime();

        DateTime endTimeJoda = new DateTime(endTime.toEpochMilli());

        ByteSourceArchiveFile archiveFile = new ByteSourceArchiveFile(file.getName(), endTimeJoda,
                Files.asByteSource(file));

        return ImmutableSet.of(archiveFile);
    }
}
