package hev.htproxy;

public class TProxyService {
    private static boolean isLoaded = false;

    static {
        try {
            System.loadLibrary("hev-socks5-tunnel");
            isLoaded = true;
        } catch (Throwable t) {
            isLoaded = false;
        }
    }

    public static void TProxyStartService(String configPath, int tunFd) {
        if (isLoaded) {
            try {
                nativeStartService(configPath, tunFd);
            } catch (Throwable ignored) {}
        }
    }

    public static void TProxyStopService() {
        if (isLoaded) {
            try {
                nativeStopService();
            } catch (Throwable ignored) {}
        }
    }

    private static native void nativeStartService(String configPath, int tunFd);
    private static native void nativeStopService();
}
