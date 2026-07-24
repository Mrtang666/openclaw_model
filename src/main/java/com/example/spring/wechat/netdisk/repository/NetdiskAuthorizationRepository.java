package com.example.spring.wechat.netdisk.repository;

import com.example.spring.wechat.netdisk.model.NetdiskAuthState;
import com.example.spring.wechat.netdisk.model.NetdiskAuthorization;
import com.example.spring.wechat.netdisk.model.NetdiskPendingAction;

import java.util.Optional;

public interface NetdiskAuthorizationRepository {

    Optional<NetdiskAuthorization> findActive(String userId, String provider);

    NetdiskAuthorization saveOrUpdate(NetdiskAuthorization authorization);

    Optional<NetdiskAuthorization> findById(long id);

    Optional<NetdiskAuthState> findAuthState(String state);

    NetdiskAuthState saveAuthState(NetdiskAuthState state);

    void markAuthStateUsed(long id);

    NetdiskPendingAction savePendingAction(NetdiskPendingAction action);

    Optional<NetdiskPendingAction> findPendingAction(long id);

    void markPendingActionRunning(long id, java.time.Instant updatedAt);

    void markPendingActionDone(long id, String resultMessage, java.time.Instant updatedAt);

    void markPendingActionFailed(long id, String errorMessage, java.time.Instant updatedAt);
}
