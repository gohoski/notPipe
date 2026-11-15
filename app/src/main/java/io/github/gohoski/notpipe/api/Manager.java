package io.github.gohoski.notpipe.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.github.gohoski.notpipe.NotPipe;
import io.github.gohoski.notpipe.R;
import io.github.gohoski.notpipe.config.Config;
import io.github.gohoski.notpipe.config.ConfigManager;
import io.github.gohoski.notpipe.http.HttpClient;
import io.github.gohoski.notpipe.http.VideoTooLongException;

/**
 * Manager class for handling multiple API instances with fallback capability.
 */
public class Manager {
    private static Manager instance;
    private Random random;
    private ConfigManager configManager;

    private List<Metadata> metadataInstances;
    private List<VideoStream> videoInstances; //360p only
    private List<VideoStream> hqInstances; //high-quality instances (480p+)
    private List<Conversion> conversionInstances; // Tracks instances capable of conversion
    private List<ChannelApi> channelApiInstances; // Tracks instances capable of ChannelApi

    private Manager() {
        random = new Random();
    }

    public static void init() {
        if (instance == null) {
            instance = new Manager();
            instance.initializeInstances();
        }
    }

    public static Manager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Manager.init() must be called first!");
        }
        return instance;
    }

    private void initializeInstances() {
        if (metadataInstances == null) {
            metadataInstances = new ArrayList<Metadata>();
            videoInstances = new ArrayList<VideoStream>();
            hqInstances = new ArrayList<VideoStream>();
            conversionInstances = new ArrayList<Conversion>();
            channelApiInstances = new ArrayList<ChannelApi>();
        } else {
            metadataInstances.clear();
            videoInstances.clear();
            hqInstances.clear();
            conversionInstances.clear();
            channelApiInstances.clear();
        }

        if (configManager == null) {
            configManager = ConfigManager.getInstance();
        }

        Config config = configManager.getConfig();

        List<String> invInstances = config.getInvidiousInstances();
        for (int i = 0; i < invInstances.size(); i++) {
            Invidious inv = new Invidious(invInstances.get(i));
            metadataInstances.add(inv);
            videoInstances.add(inv);
        }

        List<String> yt2009List = config.getYt2009Instances();
        for (int i = 0; i < yt2009List.size(); i++) {
            Yt2009 yt2009 = new Yt2009(yt2009List.get(i));
            videoInstances.add(yt2009);
            hqInstances.add(yt2009);
            conversionInstances.add(yt2009);
            channelApiInstances.add(yt2009);
        }

        List<String> pipedList = config.getPipedInstances();
        for (int i = 0; i < pipedList.size(); i++) {
            String[] parts = pipedList.get(i).split(",", 2);
            String baseUrl = parts[0];
            String proxyUrl = parts.length > 1 ? parts[1] : parts[0];
            Piped piped = new Piped(baseUrl, proxyUrl);
            metadataInstances.add(piped);
            videoInstances.add(piped);
        }

        List<String> typeTypeList = config.getTypeTypeInstances();
        for (int i = 0; i < typeTypeList.size(); i++) {
            TypeType typeType = new TypeType(typeTypeList.get(i));
            metadataInstances.add(typeType);
            videoInstances.add(typeType);
            hqInstances.add(typeType);
            channelApiInstances.add(typeType);
        }

        List<String> ytApiInstances = config.getYtApiLegacyInstances();
        for (int i = 0; i < ytApiInstances.size(); i++) {
            YtApiLegacy ytApi = new YtApiLegacy(ytApiInstances.get(i));
            metadataInstances.add(ytApi);
            videoInstances.add(ytApi);
            hqInstances.add(ytApi);
            conversionInstances.add(ytApi);
        }
    }

    public Metadata getMetadata() {
        if (metadataInstances.isEmpty()) throw new IllegalStateException("No Metadata instances");
        return createStatefulFallbackProxy(Metadata.class, metadataInstances);
    }

    /**
     * Retrieves a single raw Metadata instance without a dynamic fallback proxy wrapper.
     * Useful for optional single-attempt fallback operations (like channel icons).
     */
    public Metadata getRandomMetadata() {
        if (metadataInstances == null || metadataInstances.isEmpty()) {
            return null;
        }
        return metadataInstances.get(random.nextInt(metadataInstances.size()));
    }

    /**
     * Retrieves a Metadata proxy prioritizing YtApiLegacy instances.
     * Returns null if no YtApiLegacy instances are configured.
     */
    public Metadata getPopularMetadata() {
        if (metadataInstances == null || metadataInstances.isEmpty()) {
            return null;
        }

        List<Metadata> ytApiList = new ArrayList<Metadata>();
        for (int i = 0; i < metadataInstances.size(); i++) {
            Metadata m = metadataInstances.get(i);
            if (m instanceof YtApiLegacy) {
                ytApiList.add(m);
            }
        }

        if (ytApiList.isEmpty()) {
            return null;
        }

        return createStatefulFallbackProxy(Metadata.class, ytApiList);
    }

    public VideoStream getVideoStream() {
        if (videoInstances.isEmpty()) throw new IllegalStateException("No VideoStream instances");
        return createStatefulFallbackProxy(VideoStream.class, videoInstances);
    }

    public ChannelApi getChannelApi() {
        if (channelApiInstances.isEmpty()) throw new IllegalStateException("No ChannelApi instances");
        return createStatefulFallbackProxy(ChannelApi.class, channelApiInstances);
    }

    public List<Conversion> getConversion() {
        return conversionInstances;
    }

    /**
     * Notified each time a failover loop starts a fetch attempt against another instance.
     */
    public interface InstanceAttemptListener {
        void onInstanceAttempt(VideoStream instance);
    }

    public String getVideoUrl(String videoId, String quality, int timeout, VideoStream[] successfulInstance) throws IOException {
        return getVideoUrl(videoId, quality, timeout, successfulInstance, null);
    }

    public String getVideoUrl(String videoId, String quality, int timeout, VideoStream[] successfulInstance, InstanceAttemptListener attemptListener) throws IOException {
        List<VideoStream> targetList = "360".equals(quality) ? new ArrayList<VideoStream>(videoInstances) : new ArrayList<VideoStream>(hqInstances);
        if (targetList.isEmpty()) {
            reloadInstances();
            throw new IOException("No video instances left");
        }

        java.util.Collections.shuffle(targetList, random);

        Throwable lastError = null;

        for (int i = 0; i < targetList.size(); i++) {
            VideoStream currentInstance = targetList.get(i);
            if (attemptListener != null) attemptListener.onInstanceAttempt(currentInstance);
            try {
                String url = currentInstance.getVideoUrl(videoId, quality, timeout);
                if (successfulInstance != null && successfulInstance.length > 0) {
                    successfulInstance[0] = currentInstance;
                }
                return url;
            } catch (ContentUnavailableException e) {
                e.printStackTrace();
                throw e;
            } catch (FileNotFoundException e) {
                throw e;
            } catch (Exception e) {
                if (isDeadInstanceError(e)) {
                    removeDeadInstance(currentInstance);
                }
                lastError = e;
            }
        }

        if (lastError != null) {
            if (videoInstances.isEmpty() || hqInstances.isEmpty()) {
                reloadInstances();
                showConnectionErrorToast();
            }
            if (lastError instanceof IOException) throw (IOException) lastError;
            throw new IOException(lastError.getMessage());
        }
        throw new IOException("All instances failed");
    }

    public String getConvUrl(String videoId, String quality, Codec codec, VideoStream[] successfulInstance) throws IOException {
        return getConvUrl(videoId, quality, codec, successfulInstance, null);
    }

    public String getConvUrl(String videoId, String quality, Codec codec, VideoStream[] successfulInstance, InstanceAttemptListener attemptListener) throws IOException {
        if (configManager.getConfig().isConvertVideos()) {
            if (conversionInstances.isEmpty()) {
                reloadInstances();
                throw new IOException("No conversion instances left");
            }

            List<Conversion> targetConvList = new ArrayList<Conversion>(conversionInstances);

            java.util.Collections.shuffle(targetConvList, random);

            for (int i = 0; i < targetConvList.size(); i++) {
                Conversion currentInstance = targetConvList.get(i);
                if (attemptListener != null && currentInstance instanceof VideoStream) {
                    attemptListener.onInstanceAttempt((VideoStream) currentInstance);
                }
                try {
                    String url = currentInstance.getConvUrl(videoId, codec);
                    if (successfulInstance != null && successfulInstance.length > 0 && currentInstance instanceof VideoStream) {
                        successfulInstance[0] = (VideoStream) currentInstance;
                    }
                    return url;
                } catch (Exception e) {
                    if (e instanceof ContentUnavailableException) {
                        throw (ContentUnavailableException) e;
                    } if (e instanceof VideoTooLongException) {
                        throw (VideoTooLongException) e;
                    } conversionInstances.remove(currentInstance);
                    if (isDeadInstanceError(e)) {
                        removeDeadInstance(currentInstance);
                    }
                }
            }
        }

        // Fall back to standard stream URLs if conversion is disabled or if all conversion attempts fail
        return getVideoUrl(videoId, quality, HttpClient.CONVERSION_TIMEOUT, successfulInstance, attemptListener);
    }

    /**
     Creates a dynamic proxy that retains one instance for the lifespan of the Activity.
     */
    @SuppressWarnings("unchecked")
    private <T> T createStatefulFallbackProxy(final Class<T> interfaceClass, final List<T> pool) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{ interfaceClass },
                new StatefulFallbackHandler<T>(interfaceClass, pool)
        );
    }

    /**
     * Invocation handler backing the stateful fallback proxies, retaining one instance per proxy.
     */
    private class StatefulFallbackHandler<T> implements InvocationHandler {
        private final Class<T> interfaceClass;
        private final List<T> pool;
        private T currentInstance = null;

        StatefulFallbackHandler(Class<T> interfaceClass, List<T> pool) {
            this.interfaceClass = interfaceClass;
            this.pool = pool;
        }

        Object getCurrentInstance() {
            return currentInstance;
        }

        private void pickNewInstance() {
            if (!pool.isEmpty()) {
                currentInstance = pool.get(random.nextInt(pool.size()));
            } else {
                currentInstance = null;
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (currentInstance == null) pickNewInstance();

            Throwable lastError = null;
            List<T> localPool = new ArrayList<T>(pool);

            while (!localPool.isEmpty()) {
                if (currentInstance == null || !localPool.contains(currentInstance)) {
                    currentInstance = localPool.get(random.nextInt(localPool.size()));
                }

                try {
                    return method.invoke(currentInstance, args);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof ContentUnavailableException) {
                        throw e.getCause();
                    }
                    e.printStackTrace();
                    lastError = e.getCause();
                    localPool.remove(currentInstance);

                    if (isDeadInstanceError(lastError)) {
                        removeDeadInstance(currentInstance);
                    }
                    currentInstance = null;
                }
            }

            if (pool.isEmpty()) {
                reloadInstances();
                showConnectionErrorToast();
            }

            if (lastError != null) throw lastError;
            throw new IOException("No available instances left for " + interfaceClass.getSimpleName());
        }
    }

    /**
     * Returns the raw instance currently retained by a stateful fallback proxy,
     * or null when the given object is not such a proxy or hasn't served a request yet.
     */
    public Object getCurrentInstance(Object proxy) {
        if (proxy == null || !Proxy.isProxyClass(proxy.getClass())) return null;
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        if (handler instanceof StatefulFallbackHandler) {
            return ((StatefulFallbackHandler) handler).getCurrentInstance();
        }
        return null;
    }

    /**
     * Resolves the VideoStream capability of the instance currently backing the given
     * Metadata fallback proxy, or null when it cannot serve video streams.
     */
    public VideoStream resolveVideoStream(Metadata metadata) {
        Object current = getCurrentInstance(metadata);
        return current instanceof VideoStream ? (VideoStream) current : null;
    }

    public void removeDeadInstance(Object instance) {
        final String host;
        try {
            host = (String) instance.getClass().getMethod("getHost").invoke(instance);
            notifyDeadInstance(host);
        } catch (Exception ignored) { return; }
        removeByHost(metadataInstances, host);
        removeByHost(videoInstances, host);
        removeByHost(hqInstances, host);
        removeByHost(conversionInstances, host);
        removeByHost(channelApiInstances, host);
    }

    private void removeByHost(List list, String host) {
        java.util.Iterator iterator = list.iterator();
        while (iterator.hasNext()) {
            try {
                Object item = iterator.next();
                String itemHost = (String) item.getClass().getMethod("getHost").invoke(item);
                if (host.equals(itemHost)) {
                    iterator.remove();
                }
            } catch (Exception ignored) {}
        }
    }

    private void notifyDeadInstance(final String name) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Context context = NotPipe.getAppContext();
                    if (context != null) {
                        Toast.makeText(context,
                                context.getString(R.string.dead_instance, name),
                                Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void showConnectionErrorToast() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(NotPipe.getAppContext(),
                            "check your internet connection",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
            }
        });
    }

    private boolean isDeadInstanceError(Throwable t) {
        if (t == null) return false;

        if (!io.github.gohoski.notpipe.Utils.hasConnection(NotPipe.getAppContext())) {
            return false;
        }

        if (t instanceof SocketTimeoutException ||
                t instanceof ConnectException ||
                t instanceof UnknownHostException) {
            return true;
        }

        return false;
    }

    public void reloadInstances() {
        initializeInstances();
    }

    public List<VideoStream> getVideoInstances() {
        return videoInstances;
    }

    public List<VideoStream> getHqInstances() {
        return hqInstances;
    }

    public static class InstanceInfo {
        public VideoStream instance;
        public String host;
        public String name;
        public boolean supportsAllQualities;
    }

    public List<InstanceInfo> videoInstancesInfo() {
        initializeInstances();
        List<InstanceInfo> result = new ArrayList<InstanceInfo>();
        List<VideoStream> allKnown = new ArrayList<VideoStream>();
        List<String> processed = new ArrayList<String>();
        allKnown.addAll(videoInstances);
        allKnown.addAll(hqInstances);

        for (int i = 0; i < allKnown.size(); i++) {
            VideoStream instance = allKnown.get(i);
            InstanceInfo info = new InstanceInfo();
            info.instance = instance;
            info.host = instance.getHost();
            info.name = instance.getName();
            info.supportsAllQualities = !(instance instanceof Invidious) && !(instance instanceof Piped);
            if (!processed.contains(info.host)) {
                result.add(info);
                processed.add(info.host);
            }
        }

        return result;
    }
}