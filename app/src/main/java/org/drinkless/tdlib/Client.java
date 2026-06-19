package org.drinkless.tdlib;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class Client {
    private static final String TAG = "TDLibClient";
    private static boolean nativeLoaded = false;

    static {
        try {
            System.loadLibrary("tdjson");
            nativeLoaded = true;
            Log.i(TAG, "Successfully loaded native tdjson JNI library!");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native library 'tdjson' could not be loaded. Operating in automated simulation mode.");
        }
    }

    public static boolean isNativeLoaded() {
        return nativeLoaded;
    }

    public interface ResultHandler {
        void onResult(TdApi.Object object);
    }

    public interface ExceptionHandler {
        void onException(Throwable e);
    }

    private final int clientId;
    private final ResultHandler updateHandler;
    private final ExceptionHandler updateExceptionHandler;
    private final ExceptionHandler defaultExceptionHandler;
    private volatile boolean isDestroyed = false;

    private static final ConcurrentHashMap<Long, ResultHandler> handlers = new ConcurrentHashMap<>();
    private static final AtomicLong queryId = new AtomicLong(1);

    // Active Clients registry for simulator dispatch
    private static final ConcurrentHashMap<Integer, Client> clients = new ConcurrentHashMap<>();
    private static final AtomicInteger clientCounter = new AtomicInteger(1);

    private Client(ResultHandler updateHandler, ExceptionHandler updateExceptionHandler, ExceptionHandler defaultExceptionHandler) {
        this.updateHandler = updateHandler;
        this.updateExceptionHandler = updateExceptionHandler;
        this.defaultExceptionHandler = defaultExceptionHandler;

        if (nativeLoaded) {
            this.clientId = nativeCreate();
            clients.put(this.clientId, this);
            startReceiveLoop();
        } else {
            this.clientId = clientCounter.getAndIncrement();
            clients.put(this.clientId, this);
            startSimulatedUpdates();
        }
    }

    public static Client create(ResultHandler updateHandler, ExceptionHandler updateExceptionHandler, ExceptionHandler defaultExceptionHandler) {
        return new Client(updateHandler, updateExceptionHandler, defaultExceptionHandler);
    }

    public void send(TdApi.Function query, ResultHandler resultHandler) {
        if (isDestroyed) {
            if (defaultExceptionHandler != null) {
                defaultExceptionHandler.onException(new IllegalStateException("Client is already destroyed"));
            }
            return;
        }

        long id = queryId.getAndIncrement();
        if (resultHandler != null) {
            handlers.put(id, resultHandler);
        }

        if (nativeLoaded) {
            nativeSend(clientId, id, query);
        } else {
            // Emulate response via simulation engine
            com.example.simulator.TdLibSimulationEngine.handleQuery(this, id, query, resultHandler);
        }
    }

    private void startReceiveLoop() {
        new Thread(() -> {
            while (!isDestroyed) {
                try {
                    TdApi.Object object = nativeReceive(clientId, 1.0);
                    if (object != null) {
                        dispatchResult(object);
                    }
                } catch (Throwable e) {
                    if (updateExceptionHandler != null) {
                        updateExceptionHandler.onException(e);
                    }
                }
            }
        }, "TDLib-Receive-Loop").start();
    }

    private void dispatchResult(TdApi.Object object) {
        // Standard TDLib native bindings route queries and general background updates here
        // Inside a real environment, native library passes responses back.
        if (updateHandler != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    updateHandler.onResult(object);
                } catch (Exception e) {
                    if (updateExceptionHandler != null) {
                        updateExceptionHandler.onException(e);
                    }
                }
            });
        }
    }

    public void dispatchUpdate(TdApi.Object update) {
        if (updateHandler != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    updateHandler.onResult(update);
                } catch (Exception e) {
                    if (updateExceptionHandler != null) {
                        updateExceptionHandler.onException(e);
                    }
                }
            });
        }
    }

    private void startSimulatedUpdates() {
        com.example.simulator.TdLibSimulationEngine.registerClient(this);
    }

    public void destroy() {
        isDestroyed = true;
        clients.remove(clientId);
        if (nativeLoaded) {
            nativeDestroy(clientId);
        } else {
            com.example.simulator.TdLibSimulationEngine.unregisterClient(this);
        }
    }

    // Native JNI definitions (Exact signatures matching standard drinkless TDLib)
    private static native int nativeCreate();
    private static native void nativeSend(int clientId, long requestId, TdApi.Function function);
    private static native TdApi.Object nativeReceive(int clientId, double timeout);
    private static native void nativeExecute(TdApi.Function function);
    private static native void nativeDestroy(int clientId);
}
