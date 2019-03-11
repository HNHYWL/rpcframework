package ipctest;


import org.apache.log4j.Logger;
import rpc.ConnectionHeader;
import rpc.RPC;

import javax.net.SocketFactory;
import java.net.InetSocketAddress;
import java.util.List;


public class Client extends Thread {
    private static Logger logger = Logger.getLogger(Client.class);

    public static void main(String[] args) throws Exception {
        MyProtocol client = (MyProtocol) RPC.getProxy(MyProtocol.class,
                3,
                new InetSocketAddress("localhost", 8888),
                SocketFactory.getDefault());

        List<String> kdi = client.getList("kdi");
        System.out.println(kdi);


        //for (int i = 1; i < 20; i++) {
        //    new Thread() {
        //        @Override
        //        public void run() {
        //            while (true) {
        //                logger.info("\n\nRES-----------" + client.getPerson("wangliang", 8));
        //                logger.info("\n\nRES-----------" + client.getPersonByName("wwww"));
        //                logger.info("\n\nRES-----------" + client.getPersonByAge(88));
        //            }
        //
        //        }
        //    }.start();
        //}


        //RPC.stopProxy(client);

    }
}
