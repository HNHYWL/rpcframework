package ipctest;

import java.io.IOException;

public class MyProtocolImpl implements MyProtocol {
    @Override
    public String getName(String prefix) {
        return prefix + "test";
    }

    @Override
    public long getProtocolVersion(String protocol, long clientVersion) throws IOException {
        return clientVersion;
    }
}
