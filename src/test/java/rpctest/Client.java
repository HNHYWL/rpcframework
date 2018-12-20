package rpctest;


import ipc.Invocation;
import ipc.ObjectAndBytesUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Socket;

interface MyProtocol {
    public Person getPerson(String name, int age);
}

class Person implements Serializable {
    String name;
    int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public String toString() {
        return "Person{" +
                "name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
}

class Invoke implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        Invocation invocation = new Invocation(method, args);
        System.out.println(invocation);

        Socket socket = new Socket("localhost", 9999);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        byte[] bytes = ObjectAndBytesUtils.getBytesFromObject(invocation);
        out.writeInt(bytes.length + 4);
        int id = 90;
        out.writeInt(id);

        out.write(bytes);

        DataInputStream in = new DataInputStream(socket.getInputStream());
        id = in.readInt();
        System.out.println("come back id: "+ id);

        int len = in.readInt();
        System.out.println("come back len:" + len);
        byte[] comeBackbytes = new byte[len];
        in.readFully(comeBackbytes);

        Object objectFromBytes = ObjectAndBytesUtils.getObjectFromBytes(comeBackbytes, 0);

        socket.close();
        return objectFromBytes;
    }
}



public class Client {
    public static void main(String[] args) {
        MyProtocol myProtocol = (MyProtocol)Proxy.newProxyInstance(MyProtocol.class.getClassLoader(),
                new Class[]{MyProtocol.class}, new Invoke());

        System.out.println(myProtocol.getPerson("wangdfgrewliang", 90));
    }
}
