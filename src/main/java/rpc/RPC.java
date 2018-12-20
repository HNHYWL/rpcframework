
package rpc;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;

import java.net.InetSocketAddress;
import java.io.*;
import java.util.Map;
import java.util.HashMap;

import javax.net.SocketFactory;

import org.apache.commons.logging.*;

public class RPC {
    private static final Log LOG = LogFactory.getLog(RPC.class);

    private RPC() {
    }    // no public ctor

    /* Cache a client using its socket factory as the hash key */
    static private class ClientCache {
        private Map<SocketFactory, Client> clients = new HashMap<SocketFactory, Client>();

        private synchronized Client getClient(SocketFactory factory) {
            // Construct & cache client.  The configuration is only used for timeout,
            // and Clients have connection pools.  So we can either (a) lose some
            // connection pooling and leak sockets, or (b) use the same timeout for all
            // configurations.  Since the IPC is usually intended globally, not
            // per-job, we choose (a).
            Client client = clients.get(factory);
            if (client == null) {
                client = new Client(factory);
                clients.put(factory, client);
            } else {
                client.incCount();
            }
            return client;
        }


        /**
         * Stop a RPC client connection
         * A RPC client is closed only when its reference count becomes zero.
         */
        private void stopClient(Client client) {
            synchronized (this) {
                client.decCount();
                if (client.isZeroReference()) {
                    clients.remove(client.getSocketFactory());
                }
            }
            if (client.isZeroReference()) {
                client.stop();
            }
        }
    }

    private static ClientCache CLIENTS = new ClientCache();

    /**
     * 动态代理的InvocationHandler
     */
    private static class Invoker implements InvocationHandler {
        private InetSocketAddress address;
        private Client client;
        private boolean isClosed = false;

        public Invoker(InetSocketAddress address, SocketFactory factory) {
            this.address = address;
            this.client = CLIENTS.getClient(factory);
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            final boolean logDebug = LOG.isDebugEnabled();
            long startTime = 0;
            if (logDebug) {
                startTime = System.currentTimeMillis();
            }
            Object value = client.call(new Invocation(method, args), address, method.getDeclaringClass());
            if (logDebug) {
                long callTime = System.currentTimeMillis() - startTime;
                LOG.debug("Call: " + method.getName() + " " + callTime);
            }
            return value;
        }

        /* close the IPC client that's responsible for this invoker's RPCs */
        synchronized private void close() {
            if (!isClosed) {
                isClosed = true;
                CLIENTS.stopClient(client);
            }
        }
    }

    /**
     * rpc版本检查异常
     * A version mismatch for the RPC protocol.
     */
    public static class VersionMismatch extends IOException {
        private String interfaceName;
        private long clientVersion;
        private long serverVersion;


        public VersionMismatch(String interfaceName, long clientVersion,
                               long serverVersion) {
            super("Protocol " + interfaceName + " version mismatch. (client = " +
                    clientVersion + ", server = " + serverVersion + ")");
            this.interfaceName = interfaceName;
            this.clientVersion = clientVersion;
            this.serverVersion = serverVersion;
        }
    }

    /**
     * 生成远程代理对象
     */
    public static VersionedProtocol getProxy(Class<?> protocol,
                                             long clientVersion,
                                             InetSocketAddress addr,
                                             SocketFactory factory) throws IOException {

        VersionedProtocol proxy = (VersionedProtocol) Proxy.newProxyInstance(
                protocol.getClassLoader(),
                new Class[]{protocol},
                new Invoker(addr, factory));

        // 连接上的第一次方法调用，获取服务端的版本号，与本地指定的cliet版本号对比
        long serverVersion = proxy.getProtocolVersion(protocol.getName(), clientVersion);

        if (serverVersion == clientVersion) {
            return proxy;
        } else {
            throw new VersionMismatch(protocol.getName(), clientVersion, serverVersion);
        }
    }


    /**
     * Stop this proxy and release its invoker's resource
     *
     * @param proxy the proxy to be stopped
     */
    public static void stopProxy(VersionedProtocol proxy) {
        if (proxy != null) {
            ((Invoker) Proxy.getInvocationHandler(proxy)).close();
        }
    }


    /**
     * An RPC Server.
     */
    public static class Server extends rpc.Server {
        private Object instance;

        /**
         * Construct an RPC server.
         *
         * @param instance    the instance whose methods will be called
         * @param bindAddress the address to bind on to listen for connection
         * @param port        the port to listen for connections on
         * @param numHandlers the number of method handler threads to run
         */
        public Server(Object instance, String bindAddress, int port,
                      int numHandlers) throws IOException {
            super(bindAddress, port, Invocation.class, numHandlers);
            this.instance = instance;

        }

        @Override
        public Object call(Class<?> protocol, Invocation param, long receivedTime)
                throws IOException {
            try {
                Invocation call = param;

                Method method = protocol.getMethod(call.getMethodName(),
                        call.getParameterClasses());
                method.setAccessible(true);

                long startTime = System.currentTimeMillis();
                Object value = method.invoke(instance, call.getParameters());


                int processingTime = (int) (System.currentTimeMillis() - startTime);
                int qTime = (int) (startTime - receivedTime);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Served: " + call.getMethodName() +
                            " queueTime= " + qTime +
                            " procesingTime= " + processingTime);
                }
                return value;

            } catch (InvocationTargetException e) {
                Throwable target = e.getTargetException();
                if (target instanceof IOException) {
                    throw (IOException) target;
                } else {
                    IOException ioe = new IOException(target.toString());
                    ioe.setStackTrace(target.getStackTrace());
                    throw ioe;
                }
            } catch (Throwable e) {
                IOException ioe = new IOException(e.toString());
                ioe.setStackTrace(e.getStackTrace());
                throw ioe;
            }
        }


    }

    /**
     * 获取服务端实例
     * @param instance
     * @param bindAddress
     * @param port
     * @param numHandlers
     * @return
     * @throws IOException
     */
    public static Server getServer(final Object instance, final String bindAddress, final int port,
                                   final int numHandlers)
            throws IOException {
        return new Server(instance, bindAddress, port, numHandlers);
    }

}
