package ipc;

import java.io.*;


/**
 * 字节数组与对象相互转化的工具类
 */
public class ObjectAndBytesUtils {
    public static byte[] getBytesFromObject(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        return baos.toByteArray();
    }

    public static Object getObjectFromBytes(byte[] bytes, int offset) throws IOException,
            ClassNotFoundException {
        int length = bytes.length - offset;
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes, offset, length);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return ois.readObject();
    }
}
