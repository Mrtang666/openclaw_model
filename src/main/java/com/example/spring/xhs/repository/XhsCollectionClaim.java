package com.example.spring.xhs.repository;

import com.example.spring.xhs.model.XhsCollectionJob;

public record XhsCollectionClaim(XhsCollectionJob job, String token) {
}
