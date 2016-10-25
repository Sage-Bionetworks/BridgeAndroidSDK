package org.sagebionetworks.bridge.researchstack.upload;

import java.util.List;

public interface UploadQueue {
  List<UploadRequest> loadUploadRequests();

  void saveUploadRequest(UploadRequest request);

  void deleteUploadRequest(UploadRequest request);
}
