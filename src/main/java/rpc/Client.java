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

package rpc;

import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.ConnectException;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.InputStream;

import java.rmi.RemoteException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.SocketFactory;

import org.apache.commons.logging.*;



public class Client {
    public static final Log LOG = LogFactory.getLog(Client.class);
    private Hashtable<ConnectionId, Connection> connections = new Hashtable<ConnectionId, Connection>();

    private int counter;                            // counter for call ids
    private AtomicBoolean running = new AtomicBoolean(true); // if client runs
    final private int maxIdleTime; //connections will be culled if it was idle for maxIdleTime msecs
    final private int maxRetries; //the max. no. of retries for socket connections
    private boolean tcpNoDelay; // if T then disable Nagle's Algorithm
    private int pingInterval; // how often sends ping to the server in msecs

    private SocketFactory socketFactory;           // how to create sockets
    private int refCount = 1;

    final private static String PING_INTERVAL_NAME = "ipc.ping.interval";
    final static int DEFAULT_PING_INTERVAL = 6000; // 1 min
    final static int PING_CALL_ID = -1;




    /**
     * Increment this client's reference count
     */
    synchronized void incCount() {
        refCount++;
    }

    /**
     * Decrement this client's reference count
     */
    synchronized void decCount() {
        refCount--;
    }

    /**
     * Return if this client has no reference
     *
     * @return true if this client has no reference; false otherwise
     */
    synchronized boolean isZeroReference() {
        return refCount == 0;
    }

    /**
     * A call waiting for a value.
     */
    private class Call {
        int id;                                       // call id
        Invocation param;                               // parameter
        Object value;                               // value, null if error
        IOException error;                            // exception, null if value
        boolean done;                                 // true when call is done

        protected Call(Invocation param) {
            this.param = param;
            synchronized (Client.this) {
                this.id = counter++;
                LOG.error("current id="+ id);
            }
        }

        /**
         * Indicate when the call is complete and the
         * value or error are available.  Notifies by default.
         */
        protected synchronized void callComplete() {
            this.done = true;
            notify();                                 // notify caller
        }

        /**
         * Set the exception when there is an error.
         * Notify the caller the call is done.
         *
         * @param error exception thrown by the call; either local or remote
         */
        public synchronized void setException(IOException error) {
            this.error = error;
            callComplete();
        }

        /**
         * Set the return value when there is no error.
         * Notify the caller the call is done.
         *
         * @param value return value of the call.
         */
        public synchronized void setValue(Object value) {
            this.value = value;
            callComplete();
        }
    }

    /**
     * Thread that reads responses and notifies callers.  Each connection owns a
     * socket connected to a remote address.  Calls are multiplexed through this
     * socket: responses may be delivered out of order.
     */
    private class Connection extends Thread {
        private InetSocketAddress server;             // server ip:port
        private ConnectionHeader header;              // connection header
        private ConnectionId remoteId;                // connection id

        private Socket socket = null;                 // connected socket
        private DataInputStream in;
        private DataOutputStream out;

        // currently active calls
        private Hashtable<Integer, Call> calls = new Hashtable<Integer, Call>();
        private AtomicLong lastActivity = new AtomicLong();// last I/O activity time
        private AtomicBoolean shouldCloseConnection = new AtomicBoolean();  // indicate if the connection is closed
        private IOException closeException; // close reason

        public Connection(ConnectionId remoteId) throws IOException {
            this.remoteId = remoteId;
            this.server = remoteId.getAddress();
            if (server.isUnresolved()) {
                throw new UnknownHostException("unknown host: " + remoteId.getAddress().getHostName());
            }

            Class<?> protocol = remoteId.getProtocol();
            header = new ConnectionHeader(protocol == null ? null : protocol.getName());

            this.setName("IPC Client (" + socketFactory.hashCode() + ") connection to " +
                    remoteId.getAddress().toString() +
                    " from ");
            this.setDaemon(true);
        }

        /**
         * Update lastActivity with the current time.
         */
        private void touch() {
            lastActivity.set(System.currentTimeMillis());
        }

        /**
         * Add a call to this connection's call queue and notify
         * a listener; synchronized.
         * Returns false if called during shutdown.
         *
         * @param call to add
         * @return true if the call was added.
         */
        private synchronized boolean addCall(Call call) {
            if (shouldCloseConnection.get())
                return false;
            calls.put(call.id, call);
            notify();
            return true;
        }

        /**
         * This class sends a ping to the remote side when timeout on
         * reading. If no failure is detected, it retries until at least
         * a byte is read.
         */
        private class PingInputStream extends FilterInputStream {
            /* constructor */
            protected PingInputStream(InputStream in) {
                super(in);
            }

            /* Process timeout exception
             * if the connection is not going to be closed, send a ping.
             * otherwise, throw the timeout exception.
             */
            private void handleTimeout(SocketTimeoutException e) throws IOException {
                if (shouldCloseConnection.get() || !running.get()) {
                    throw e;
                } else {
                    sendPing();
                }
            }

            /**
             * Read a byte from the stream.
             * Send a ping if timeout on read. Retries if no failure is detected
             * until a byte is read.
             *
             * @throws IOException for any IO problem other than socket timeout
             */
            public int read() throws IOException {
                do {
                    try {
                        return super.read();
                    } catch (SocketTimeoutException e) {
                        handleTimeout(e);
                    }
                } while (true);
            }

            /**
             * Read bytes into a buffer starting from offset <code>off</code>
             * Send a ping if timeout on read. Retries if no failure is detected
             * until a byte is read.
             *
             * @return the total number of bytes read; -1 if the connection is closed.
             */
            public int read(byte[] buf, int off, int len) throws IOException {
                do {
                    try {
                        return super.read(buf, off, len);
                    } catch (SocketTimeoutException e) {
                        handleTimeout(e);
                    }
                } while (true);
            }
        }

        /**
         * Connect to the server and set up the I/O streams. It then sends
         * a header to the server and starts
         * the connection thread that waits for responses.
         */
        private synchronized void setupIOstreams() {
            if (socket != null || shouldCloseConnection.get()) {
                return;
            }

            short ioFailures = 0;
            short timeoutFailures = 0;
            try {
//                if (LOG.isDebugEnabled()) {
//                    LOG.debug("Connecting to " + server);
//                }
                while (true) {
                    try {
                        this.socket = socketFactory.createSocket();
                        this.socket.setTcpNoDelay(tcpNoDelay);
                        this.socket.connect(remoteId.getAddress(),20000 );
                        // connection time out is 20s
                        //NetUtils.connect(this.socket, remoteId.getAddress(), 20000);
                        this.socket.setSoTimeout(pingInterval);
                        break;
                    } catch (SocketTimeoutException toe) {
                        /* The max number of retries is 45,
                         * which amounts to 20s*45 = 15 minutes retries.
                         */
                        handleConnectionFailure(timeoutFailures++, 45, toe);
                    } catch (IOException ie) {
                        handleConnectionFailure(ioFailures++, maxRetries, ie);
                    }
                }
                this.in = new DataInputStream(new BufferedInputStream
                        (new PingInputStream(this.socket.getInputStream())));
                this.out = new DataOutputStream
                        (new BufferedOutputStream(socket.getOutputStream()));
                writeHeader();

                // update last activity time
                touch();

                // start the receiver thread after the socket connection has been set up
                start();
            } catch (IOException e) {
                markClosed(e);
                close();
            }
        }

        /* Handle connection failures
         *
         * If the current number of retries is equal to the max number of retries,
         * stop retrying and throw the exception; Otherwise backoff 1 second and
         * try connecting again.
         *
         * This Method is only called from inside setupIOstreams(), which is
         * synchronized. Hence the sleep is synchronized; the locks will be retained.
         *
         * @param curRetries current number of retries
         * @param maxRetries max number of retries allowed
         * @param ioe failure reason
         * @throws IOException if max number of retries is reached
         */
        private void handleConnectionFailure(
                int curRetries, int maxRetries, IOException ioe) throws IOException {
            // close the current connection
            try {
                socket.close();
            } catch (IOException e) {
                LOG.warn("Not able to close a socket", e);
            }
            // set socket to null so that the next call to setupIOstreams
            // can start the process of connect all over again.
            socket = null;

            // throw the exception if the maximum number of retries is reached
            if (curRetries >= maxRetries) {
                throw ioe;
            }

            // otherwise back off and retry
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }

            LOG.info("Retrying connect to server: " + server +
                    ". Already tried " + curRetries + " time(s).");
        }

        private void writeHeader() throws IOException {
            out.write(Server.HEADER.array());
            out.write(Server.CURRENT_VERSION);

            byte[] bytesFromObject = ObjectAndBytesUtils.getBytesFromObject(header);
            LOG.info(header);
            LOG.info("len header:" + bytesFromObject.length);
            out.writeInt(bytesFromObject.length);
            out.write(bytesFromObject);
        }


        /* wait till someone signals us to start reading RPC response or
         * it is idle too long, it is marked as to be closed,
         * or the client is marked as not running.
         *
         * Return true if it is time to read a response; false otherwise.
         */
        private synchronized boolean waitForWork() {
            if (calls.isEmpty() && !shouldCloseConnection.get() && running.get()) {
                long timeout = maxIdleTime - (System.currentTimeMillis() - lastActivity.get());
                if (timeout > 0) {
                    try {
                        wait(timeout);
                    } catch (InterruptedException e) {
                    }
                }
            }

            if (!calls.isEmpty() && !shouldCloseConnection.get() && running.get()) {
                return true;
            } else if (shouldCloseConnection.get()) {
                return false;
            } else if (calls.isEmpty()) { // idle connection closed or stopped
                markClosed(null);
                return false;
            } else { // get stopped but there are still pending requests
                markClosed((IOException) new IOException().initCause(
                        new InterruptedException()));
                return false;
            }
        }

        public InetSocketAddress getRemoteAddress() {
            return server;
        }

        /* Send a ping to the server if the time elapsed
         * since last I/O activity is equal to or greater than the ping interval
         */
        private synchronized void sendPing() throws IOException {
            long curTime = System.currentTimeMillis();
            if (curTime - lastActivity.get() >= pingInterval) {
                lastActivity.set(curTime);
                synchronized (out) {
                    LOG.error("send ping");
                    out.writeInt(PING_CALL_ID);
                    out.flush();
                }
            }
        }

        public void run() {
            if (LOG.isDebugEnabled()) LOG.debug(getName() + ": starting, having connections " + connections.size());

            while (waitForWork()) {//wait here for work - read or close connection
                LOG.error("waite for work is running");
                receiveResponse();
            }

            LOG.error("close???");
            close();

            if (LOG.isDebugEnabled())
                LOG.debug(getName() + ": stopped, remaining connections "
                        + connections.size());
        }

        public void sendParam(Call call) {
            if (shouldCloseConnection.get()) {
                return;
            }

            try {
                synchronized (this.out) {
                    if (LOG.isDebugEnabled())
                        LOG.debug(getName() + " sending #" + call.id);
                    byte[] bytes = ObjectAndBytesUtils.getBytesFromObject(call.param);
                    out.writeInt(bytes.length+4);
                    out.writeInt(call.id);
                    out.write(bytes);
                    out.flush();
                }
            } catch (IOException e) {
                markClosed(e);
            }
        }



        private void receiveResponse() {
            if (shouldCloseConnection.get()) {
                return;
            }
            touch();

            try {
                int id = in.readInt();

                if (LOG.isDebugEnabled()) LOG.debug(getName() + " got value #" + id);

                Call call = calls.get(id);

                int state = in.readInt();     // read call status
                // read value
                int len = in.readInt();
                byte[] buffer = new byte[len];
                in.readFully(buffer);
                Object value = ObjectAndBytesUtils.getObjectFromBytes(buffer, 0);

                if (state == Status.SUCCESS.state) {
                    call.setValue(value);
                    calls.remove(id);
                } else if (state == Status.ERROR.state) {
                    call.setException(new IOException(value.toString()));
                } else if (state == Status.FATAL.state) {
                    close();
                }
            } catch (IOException e) {
                markClosed(e);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        private synchronized void markClosed(IOException e) {
            if (shouldCloseConnection.compareAndSet(false, true)) {
                closeException = e;
                notifyAll();
            }
        }

        /**
         * Close the connection.
         */
        private synchronized void close(){
            if (!shouldCloseConnection.get()) {
                LOG.error("The connection is not in the closed state");
                return;
            }

            // release the resources
            // first thing to do;take the connection out of the connection list
            synchronized (connections) {
                if (connections.get(remoteId) == this) {
                    connections.remove(remoteId);
                }
            }

            // close the streams and therefore the socket
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // clean up all calls
            if (closeException == null) {

                if (!calls.isEmpty()) {
                    LOG.warn("A connection is closed for no cause and calls are not empty");

                    // clean up calls anyway
                    closeException = new IOException("Unexpected closed connection");
                    cleanupCalls();
                }
            } else {
                // log the info
                if (LOG.isDebugEnabled()) {
                    LOG.debug("closing ipc connection to " + server + ": " +
                            closeException.getMessage(), closeException);
                }

                // cleanup calls
                cleanupCalls();
            }
            if (LOG.isDebugEnabled())
                LOG.debug(getName() + ": closed");
        }

        /* Cleanup all calls and mark them as done */
        private void cleanupCalls() {
            Iterator<Entry<Integer, Call>> itor = calls.entrySet().iterator();
            while (itor.hasNext()) {
                Call c = itor.next().getValue();
                c.setException(closeException); // local exception
                itor.remove();
            }
        }
    }



    public Client(SocketFactory factory) {
        this.maxIdleTime = 10000; //10s
        this.maxRetries = 10;
        this.tcpNoDelay = false;
        this.pingInterval = 60000;
        if (LOG.isDebugEnabled()) {
            LOG.debug("The ping interval is" + this.pingInterval + "ms.");
        }
        this.socketFactory = factory;
    }



    /**
     * Return the socket factory of this client
     *
     * @return this client's socket factory
     */
    SocketFactory getSocketFactory() {
        return socketFactory;
    }

    /**
     * Stop all threads related to this client.  No further calls may be made
     * using this client.
     */
    public void stop() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping client");
        }

        if (!running.compareAndSet(true, false)) {
            return;
        }

        // wake up all connections
        synchronized (connections) {
            for (Connection conn : connections.values()) {
                conn.interrupt();
            }
        }

        // wait until all connections are closed
        while (!connections.isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
    }



    /**
     * Make a call, passing <code>param</code>, to the IPC server running at
     * <code>address</code> which is servicing the <code>protocol</code> protocol,
     * with the <code>ticket</code> credentials, returning the value.
     * Throws exceptions if there are network problems or if the remote code
     * threw an exception.
     */
    public Object call(Invocation param, InetSocketAddress addr,
                         Class<?> protocol) throws InterruptedException, IOException {
        Call call = new Call(param);
        Connection connection = getConnection(addr, protocol, call);
        connection.sendParam(call);                 // send the parameter
        boolean interrupted = false;
        synchronized (call) {
            while (!call.done) {
                try {
                    call.wait();                           // wait for the result
                } catch (InterruptedException ie) {
                    // save the fact that we were interrupted
                    interrupted = true;
                }
            }

            if (interrupted) {
                // set the interrupt flag now that we are done waiting
                Thread.currentThread().interrupt();
            }

            if (call.error != null) {
                if (call.error instanceof RemoteException) {
                    call.error.fillInStackTrace();
                    throw call.error;
                } else { // local exception
                    throw wrapException(addr, call.error);
                }
            } else {
                return call.value;
            }
        }
    }

    /**
     * Take an IOException and the address we were trying to connect to
     * and return an IOException with the input exception as the cause.
     * The new exception provides the stack trace of the place where
     * the exception is thrown and some extra diagnostics information.
     * If the exception is ConnectException or SocketTimeoutException,
     * return a new one of the same type; Otherwise return an IOException.
     *
     * @param addr      target address
     * @param exception the relevant exception
     * @return an exception to throw
     */
    private IOException wrapException(InetSocketAddress addr,
                                      IOException exception) {
        if (exception instanceof ConnectException) {
            //connection refused; include the host:port in the error
            return (ConnectException) new ConnectException(
                    "Call to " + addr + " failed on connection exception: " + exception)
                    .initCause(exception);
        } else if (exception instanceof SocketTimeoutException) {
            return (SocketTimeoutException) new SocketTimeoutException(
                    "Call to " + addr + " failed on socket timeout exception: "
                            + exception).initCause(exception);
        } else {
            return (IOException) new IOException(
                    "Call to " + addr + " failed on local exception: " + exception)
                    .initCause(exception);

        }
    }

    /**
     * Get a connection from the pool, or create a new one and add it to the
     * pool.  Connections to a given host/port are reused.
     */
    private Connection getConnection(InetSocketAddress addr,
                                     Class<?> protocol,
                                     Call call)
            throws IOException {
        if (!running.get()) {
            // the client is stopped
            throw new IOException("The client is stopped");
        }
        Connection connection;
        /* we could avoid this allocation for each RPC by having a
         * connectionsId object and with set() method. We need to manage the
         * refs for keys in HashMap properly. For now its ok.
         */
        ConnectionId remoteId = new ConnectionId(addr, protocol);
        do {
            synchronized (connections) {
                connection = connections.get(remoteId);
                if (connection == null) {
                    connection = new Connection(remoteId);
                    connections.put(remoteId, connection);
                }
            }
        } while (!connection.addCall(call));

        //we don't invoke the method below inside "synchronized (connections)"
        //block above. The reason for that is if the server happens to be slow,
        //it will take longer to establish a connection and that will slow the
        //entire system down.
        connection.setupIOstreams();
        return connection;
    }

    /**
     * This class holds the address and the user ticket. The client connections
     * to servers are uniquely identified by <remoteAddress, protocol, ticket>
     */
    private static class ConnectionId {
        InetSocketAddress address;
        Class<?> protocol;
        private static final int PRIME = 16777619;

        ConnectionId(InetSocketAddress address, Class<?> protocol
                  ) {
            this.protocol = protocol;
            this.address = address;
        }

        InetSocketAddress getAddress() {
            return address;
        }

        Class<?> getProtocol() {
            return protocol;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ConnectionId) {
                ConnectionId id = (ConnectionId) obj;
                return address.equals(id.address) && protocol == id.protocol;
                //Note : ticket is a ref comparision.
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (address.hashCode() + PRIME * System.identityHashCode(protocol));
        }
    }
}
