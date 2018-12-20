package rpc;


import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 调用信息的封装类
 */
public class Invocation implements Serializable {
    // 方法名称
    private String methodName;
    // 调用参数，空数组跟null一致
    private Object[] parameters;
    // 方法调用参数的类名，空数组==null
    private String[] parameterTypeNames;
    // 方法形式参数的类数组
    private transient Class[] parameterClasses = null;

    static class GetClassByName {
        // 基本数据类型无法Class.forname,需要单独处理
        private static final Map<String, Class<?>> WARP_TO_PRIMITIVE = new HashMap<>();

        static {
            WARP_TO_PRIMITIVE.put("int", Integer.TYPE);
            WARP_TO_PRIMITIVE.put("long", Long.TYPE);
            WARP_TO_PRIMITIVE.put("boolean", Boolean.TYPE);
            WARP_TO_PRIMITIVE.put("byte", Byte.TYPE);
            WARP_TO_PRIMITIVE.put("short", Short.TYPE);
            WARP_TO_PRIMITIVE.put("float", Float.TYPE);
            WARP_TO_PRIMITIVE.put("double", Double.TYPE);
            WARP_TO_PRIMITIVE.put("char", Character.TYPE);
        }

        static Class<?> getClassByName(String className) throws ClassNotFoundException {
            if (WARP_TO_PRIMITIVE.containsKey(className)) {
                return WARP_TO_PRIMITIVE.get(className);
            } else {
                return Class.forName(className);
            }
        }
    }


    public Invocation(Method method, Object[] parameters) {
        this.methodName = method.getName();
        this.parameters = parameters;
        this.parameterClasses = method.getParameterTypes();
        if (parameters == null) {
            parameterTypeNames = null;
        } else {
            parameterTypeNames = new String[parameters.length];
            int i = 0;
            for (Class clazz : parameterClasses) {
                parameterTypeNames[i++] = clazz.getName();
            }
        }
    }

    public Invocation(String methodName, Object[] parameters) {
        this.methodName = methodName;
        this.parameters = parameters;
        parameterClasses = new Class[parameters.length];
        int i = 0;
        for (Object o : parameters) {
            parameterClasses[i++] = o.getClass();
        }
    }

    public Class[] getParameterClasses() throws ClassNotFoundException {
        if (parameters == null) return null;
        if (parameterClasses != null) return parameterClasses;
        parameterClasses = new Class[parameters.length];
        int i = 0;
        for (String className : parameterTypeNames) {
            parameterClasses[i++] = GetClassByName.getClassByName(className);
        }
        return parameterClasses;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public String getMethodName() {
        return methodName;
    }

    @Override
    public String toString() {
        return "Invocation{" +
                "methodName='" + methodName + '\'' +
                ", parameters=" + Arrays.toString(parameters) +
                ", parameterTypeNames=" + Arrays.toString(parameterTypeNames) +
                ", parameterClasses=" + Arrays.toString(parameterClasses) +
                '}';
    }
}


