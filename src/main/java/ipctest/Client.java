package ipctest;


import ipc.RPC;

import javax.net.SocketFactory;
import java.net.InetSocketAddress;


public class Client extends Thread {
    public static void main(String[] args) throws Exception{
        IPCQueryStatus client = (IPCQueryStatus) RPC.getProxy(IPCQueryStatus.class, 3,
                new InetSocketAddress("localhost", 9090), SocketFactory.getDefault());
        String test = client.getFileStatus("test111", 3);
        System.out.println(test);


        System.out.println(client.getFileStatus("====\n\n\n====", 999999));


        RPC.stopProxy(client);





    }
}
