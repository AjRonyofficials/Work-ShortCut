package hev.htproxy;

public final class TProxyService {
    static {
        try {
            System.loadLibrary("hev-socks5-tunnel");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private TProxyService() {}

    public static native boolean TProxyStartService(String configPath, int tunFd);
    public static native boolean TProxyStopService();
    public static native boolean TProxyIsRunning();
    public static native long[] TProxyGetStats();
}
