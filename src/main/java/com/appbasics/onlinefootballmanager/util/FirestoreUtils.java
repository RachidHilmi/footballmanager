package com.appbasics.onlinefootballmanager.util;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import reactor.core.publisher.Mono;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;

import java.util.concurrent.CompletableFuture;

public class FirestoreUtils {
    public static Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }

    public static <T> Mono<T> monoFromApiFuture(ApiFuture<T> apiFuture) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        ApiFutures.addCallback(apiFuture, new ApiFutureCallback<T>() {
            @Override
            public void onFailure(Throwable t) {
                completableFuture.completeExceptionally(t);
            }
            @Override
            public void onSuccess(T result) {
                completableFuture.complete(result);
            }
        }, MoreExecutors.directExecutor());
        return Mono.fromFuture(completableFuture);
    }
}
