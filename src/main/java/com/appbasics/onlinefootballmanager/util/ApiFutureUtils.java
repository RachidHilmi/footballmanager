package com.appbasics.onlinefootballmanager.util;

import com.google.api.core.ApiFuture;
import java.util.concurrent.CompletableFuture;

public class ApiFutureUtils {
    public static <T> CompletableFuture<T> toCompletableFuture(ApiFuture<T> apiFuture) {
        CompletableFuture<T> completable = new CompletableFuture<>();
        apiFuture.addListener(() -> {
            try {
                completable.complete(apiFuture.get());
            } catch (Exception e) {
                completable.completeExceptionally(e);
            }
        }, Runnable::run);
        return completable;
    }
}
