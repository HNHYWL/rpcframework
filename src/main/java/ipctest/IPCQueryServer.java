package ipctest;




import ipc.RPC;

import java.util.concurrent.TimeUnit;

public class IPCQueryServer {
    public static final int IPC_PORT=9090;

    public static void main(String[] args) {
        try {
            IPCImpl ipc = new IPCImpl();
            RPC.Server server = RPC.getServer(ipc, "0.0.0.0", IPC_PORT, 5);
            server.start();

            TimeUnit.SECONDS.sleep(10000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
