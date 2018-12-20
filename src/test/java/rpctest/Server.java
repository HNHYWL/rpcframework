package rpctest;



import ipc.Invocation;
import ipc.ObjectAndBytesUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;

public class Server implements MyProtocol{
    @Override
    public Person getPerson(String name, int age) {
        return new Person(name + " server", +10000);
    }


    public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Server server = new Server();

        ServerSocket serverSocket = new ServerSocket(9999);
        while (true) {
            Socket accept = serverSocket.accept();
            DataInputStream in = new DataInputStream(accept.getInputStream());
            int i = in.readInt();
            System.out.println("len: "+ i);
            int id = in.readInt();
            System.out.println("id: " + id);

            byte[] bytes = new byte[i - 4];
            in.readFully(bytes);

            Invocation of = (Invocation) ObjectAndBytesUtils.getObjectFromBytes(bytes, 0);
            System.out.println(of);

            Method method = MyProtocol.class.getMethod(of.getMethodName(), of.getParameterClasses());
            Object invoke = method.invoke(server, of.getParameters());
            System.out.println(invoke);

            DataOutputStream out = new DataOutputStream(accept.getOutputStream());
            out.writeInt(id);

            byte[] bytesFromObject = ObjectAndBytesUtils.getBytesFromObject(invoke);
            out.writeInt(bytesFromObject.length);
            out.write(bytesFromObject);
            out.flush();
            accept.close();


        }
    }
}
