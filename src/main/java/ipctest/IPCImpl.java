package ipctest;

import java.io.IOException;

public class IPCImpl implements IPCQueryStatus {
    @Override
    public String getFileStatus(String filename, Integer a) {
        return filename + "....rest!" + a;
    }

    public long getProtocolVersion(String protocol, long clientVersion) throws IOException {
        System.out.println(protocol +"...,..."+clientVersion);

        return 3;
    }
}
