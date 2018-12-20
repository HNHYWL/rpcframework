package ipctest;


import ipc.VersionedProtocol;

public interface MyProtocol  extends VersionedProtocol {
    public String getName(String prefix);
}
