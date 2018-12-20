/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ipc;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

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

    private static class Invoker implements InvocationHandler {
        private InetSocketAddress address;
        private Client client;
        private boolean isClosed = false;

        public Invoker(InetSocketAddress address,
                       SocketFactory factory) {
            this.address = address;

            this.client = CLIENTS.getClient(factory);
        }

        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            final boolean logDebug = LOG.isDebugEnabled();
            long startTime = 0;
            if (logDebug) {
                startTime = System.currentTimeMillis();
            }

            Object value =  client.call(new Invocation(method, args), address,
                    method.getDeclaringClass());
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
     * A version mismatch for the RPC protocol.
     */
    public static class VersionMismatch extends IOException {
        private String interfaceName;
        private long clientVersion;
        private long serverVersion;

        /**
         * Create a version mismatch exception
         *
         * @param interfaceName the name of the protocol mismatch
         * @param clientVersion the client's version of the protocol
         * @param serverVersion the server's version of the protocol
         */
        public VersionMismatch(String interfaceName, long clientVersion,
                               long serverVersion) {
            super("Protocol " + interfaceName + " version mismatch. (client = " +
                    clientVersion + ", server = " + serverVersion + ")");
            this.interfaceName = interfaceName;
            this.clientVersion = clientVersion;
            this.serverVersion = serverVersion;
        }

        /**
         * Get the interface name
         *
         * @return the java class name
         * (eg. org.apache.hadoop.mapred.InterTrackerProtocol)
         */
        public String getInterfaceName() {
            return interfaceName;
        }

        /**
         * Get the client's preferred version
         */
        public long getClientVersion() {
            return clientVersion;
        }

        /**
         * Get the server's agreed to version.
         */
        public long getServerVersion() {
            return serverVersion;
        }
    }

    public static VersionedProtocol waitForProxy(Class protocol,
                                                 long clientVersion,
                                                 InetSocketAddress addr
    ) throws IOException {
        return waitForProxy(protocol, clientVersion, addr, Long.MAX_VALUE);
    }

    /**
     * Get a proxy connection to a remote server
     *
     * @param protocol      protocol class
     * @param clientVersion client version
     * @param addr          remote address
     * @param timeout       time in milliseconds before giving up
     * @return the proxy
     * @throws IOException if the far end through a RemoteException
     */
    static VersionedProtocol waitForProxy(Class protocol,
                                          long clientVersion,
                                          InetSocketAddress addr,
                                          long timeout
    ) throws IOException {
        long startTime = System.currentTimeMillis();
        IOException ioe;
        while (true) {
            try {
                return getProxy(protocol, clientVersion, addr, SocketFactory.getDefault());
            } catch (ConnectException se) {  // namenode has not been started
                LOG.info("Server at " + addr + " not available yet, Zzzzz...");
                ioe = se;
            } catch (SocketTimeoutException te) {  // namenode is busy
                LOG.info("Problem connecting to server: " + addr);
                ioe = te;
            }
            // check if timed out
            if (System.currentTimeMillis() - timeout >= startTime) {
                throw ioe;
            }

            // wait for retry
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                // IGNORE
            }
        }
    }


    /**
     * Construct a client-side proxy object that implements the named protocol,
     * talking to a server at the named address.
     */
    public static VersionedProtocol getProxy(Class<?> protocol,
                                             long clientVersion,
                                             InetSocketAddress addr,
                                             SocketFactory factory) throws IOException {

        VersionedProtocol proxy = (VersionedProtocol) Proxy.newProxyInstance(
                protocol.getClassLoader(),
                new Class[]{protocol},
                new Invoker(addr,factory));

        long serverVersion = proxy.getProtocolVersion(protocol.getName(), clientVersion);
        if (serverVersion == clientVersion) {
            return proxy;
        } else {
            throw new VersionMismatch(protocol.getName(), clientVersion, serverVersion);
        }
    }

    static void showSeconds() {
        for (int i = 0; i < 1000; i++) {
            LOG.error("seconds:" + i);
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
    public static class Server extends ipc.Server {
        private Object instance;
        private boolean verbose;
        private boolean authorize = false;

        /**
         * Construct an RPC server.
         *
         * @param instance    the instance whose methods will be called
         * @param bindAddress the address to bind on to listen for connection
         * @param port        the port to listen for connections on
         */
        public Server(Object instance,String bindAddress, int port)
                throws IOException {
            this(instance,bindAddress, port, 1);
        }

        private static String classNameBase(String className) {
            String[] names = className.split("\\.", -1);
            if (names == null || names.length == 0) {
                return className;
            }
            return names[names.length - 1];
        }

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
            super(bindAddress, port, Invocation.class, numHandlers,
                    classNameBase(instance.getClass().getName()));
            this.instance = instance;

        }

        @Override
        public Object call(Class<?> protocol, Invocation param, long receivedTime)
                throws IOException {
            try {
                Invocation call =  param;
                if (verbose) log("Call: " + call);


                Method method = protocol.getMethod(call.getMethodName(),
                        call.getParameterClasses());
                method.setAccessible(true);

                long startTime = System.currentTimeMillis();
                System.out.println("\n\n\n\nnew call --------\n"+call );
                LOG.info("instace calss:" + instance.getClass());
                //TimeUnit.SECONDS.sleep(5);

                Object value = method.invoke(instance, call.getParameters());

                System.out.println("=======" + value);

                int processingTime = (int) (System.currentTimeMillis() - startTime);
                int qTime = (int) (startTime - receivedTime);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Served: " + call.getMethodName() +
                            " queueTime= " + qTime +
                            " procesingTime= " + processingTime);
                }

                if (verbose) log("Return: " + value);

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

    public static Server getServer(final Object instance, final String bindAddress, final int port,
                                   final int numHandlers)
            throws IOException {
        return new Server(instance, bindAddress, port, numHandlers);
    }


    private static void log(String value) {
        if (value != null && value.length() > 55)
            value = value.substring(0, 55) + "...";
        LOG.info(value);
    }
}
