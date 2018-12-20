package ipctest;


import ipc.VersionedProtocol;

public interface IPCQueryStatus extends VersionedProtocol {
    String getFileStatus(String filename, Integer a);
}
