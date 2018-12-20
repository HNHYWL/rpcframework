package ipctest;


import rpc.VersionedProtocol;

public interface MyProtocol  extends VersionedProtocol {
    public Person getPersonByName(String name);

    public Person getPersonByAge(int age);

    Person getPerson(String name, int age);

}
