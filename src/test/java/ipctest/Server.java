package ipctest;

import org.apache.log4j.Logger;
import rpc.ConnectionHeader;
import rpc.RPC;

import java.io.IOException;

public class Server {
    private static Logger logger = Logger.getLogger(Server.class);
    public static class ServerInstance implements MyProtocol {

        @Override
        public Person getPersonByName(String name) {
            logger.info("\n\ninvoke just name -----");
            return new Person(name);
        }

        @Override
        public Person getPersonByAge(int age) {
            logger.info("\n\ninvoke just age ------");
            return new Person(age );
        }

        @Override
        public Person getPerson(String name, int age) {
            logger.info("\n\ninvoke both name and age -----");
            return new Person(name, age);
        }

        @Override
        public long getProtocolVersion(String protocol, long clientVersion) throws IOException {
            logger.info("\n\ninvoke the version -----");
            return 3;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ServerInstance serverInstance = new ServerInstance();

        RPC.Server server = RPC.getServer(serverInstance, "localhost", 8888, 10);

        server.start();

        server.join();

    }
}
