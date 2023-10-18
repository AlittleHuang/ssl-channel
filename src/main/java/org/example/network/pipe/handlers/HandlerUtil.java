package org.example.network.pipe.handlers;

import org.example.log.Logs;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class HandlerUtil {

    private static final Logger logger = Logs.getLogger(HandlerUtil.class);

    private static final Map<MethodDeclaring, Map<Class<?>, Boolean>> CACHE = new EnumMap<>(MethodDeclaring.class);


    public static boolean isDefault(MethodDeclaring method, PipeHandler handler) {
        Class<?> handlerType = handler.getClass();
        return CACHE.computeIfAbsent(method, __ -> new ConcurrentHashMap<>())
                .computeIfAbsent(handlerType, type -> {
                    try {
                        return handlerType.getMethod(method.methodName, method.parameterTypes)
                                       .getDeclaringClass() == PipeHandler.class;
                    } catch (NoSuchMethodException e) {
                        logger.log(Level.WARNING, method.name(), e);
                        return false;
                    }
                });
    }


    public enum MethodDeclaring {

        ON_RECEIVE(getMethod(() -> PipeHandler.class.getMethod("onReceive", PipeContext.class, ByteBuffer.class))),

        ON_WRITE(getMethod(() -> PipeHandler.class.getMethod("onWrite", PipeContext.class, ByteBuffer.class))),

        ON_CONNECTED(getMethod(() -> PipeHandler.class.getMethod("onConnected", PipeContext.class))),

        ON_CLOSE(getMethod(() -> PipeHandler.class.getMethod("onClose", PipeContext.class))),

        ON_ERROR(getMethod(() -> PipeHandler.class.getMethod("onError", PipeContext.class, Throwable.class))),

        ON_CONNECT(getMethod(() -> PipeHandler.class.getMethod("onConnect", PipeContext.class, InetSocketAddress.class))),

        ;

        public final String methodName;
        public final Class<?>[] parameterTypes;

        MethodDeclaring(Method method) {
            Objects.requireNonNull(method);

            this.methodName = method.getName();
            this.parameterTypes = method.getParameterTypes();
        }

        private static Method getMethod(Callable<Method> callable) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }


}
