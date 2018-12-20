package rpctest;


import ipc.Invocation;
import ipc.ObjectAndBytesUtils;

import java.io.IOException;
import java.util.Arrays;

public class Test {
    @org.junit.Test
    public void invocation() throws ClassNotFoundException, IOException {
        Invocation get = new Invocation("get", new Object[]{1, 2});
        System.out.println(get.getParameterClasses());
        System.out.println(get);
        byte[] bytes = ObjectAndBytesUtils.getBytesFromObject(get);
        System.out.println(Arrays.toString(bytes));
    }
}
