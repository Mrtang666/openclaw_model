package com.example.spring.wechat.web.repository;

import com.example.spring.wechat.web.model.WebPageContent;

import java.time.Instant;
import java.util.Optional;

/**
 * 网页正文缓存仓库，避免短时间内重复抓取同一个 URL。
 */
public interface WebPageCacheRepository {

    Optional<WebPageContent> findFresh(String urlHash, Instant now);

    void save(String urlHash, WebPageContent page, Instant expireAt);
}
