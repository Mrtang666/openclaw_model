package com.example.spring.xhs.source;

public interface XhsSourceClient {

    XhsCollectorSubmission submitSearch(XhsCollectionRequest request);

    XhsCollectorJobResult getJob(String externalJobId);

    default XhsResolvedLink resolveLink(String noteId, String query, int limit) {
        throw new UnsupportedOperationException("link resolution is not supported");
    }
}
