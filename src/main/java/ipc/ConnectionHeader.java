package ipc;

import org.apache.log4j.Logger;

import java.io.Serializable;

/**
 * 建立连接的时候先行发送的头部信息
 */

public class ConnectionHeader implements Serializable {
    private static Logger logger = Logger.getLogger(ConnectionHeader.class);
    private String protocolName;

    public ConnectionHeader() {
    }

    public ConnectionHeader(String protocolName) {
        this.protocolName = protocolName;
    }

    public String getProtocolName() {
        return protocolName;
    }

    @Override
    public String toString() {
        return "connection header:" + protocolName;
    }

    public String getProtocol() {
        return protocolName;
    }
}
