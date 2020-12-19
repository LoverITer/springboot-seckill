package top.easyblog.seckill.api.utils;

import org.slf4j.MDC;

import java.util.Map;

/**
 * Runnable 包装 MDC
 *
 * @author chenck
 * @date 2020/9/23 19:37
 */
public class RunnableWarpper implements Runnable {

    private Runnable runnable;
    private Map<String, String> contextMap;
    private Object param;

    public RunnableWarpper(Runnable runnable) {
        this.runnable = runnable;
        this.contextMap = MDC.getCopyOfContextMap();
    }

    public RunnableWarpper(Runnable runnable, Object param) {
        this.runnable = runnable;
        this.contextMap = MDC.getCopyOfContextMap();
        this.param = param;
    }

    @Override
    public void run() {
        try {
            if (null != contextMap) {
                MDC.setContextMap(contextMap);
            }
            runnable.run();
        } finally {
            if (null != contextMap) {
                MDC.clear();
            }
        }
    }

    public Object getParam() {
        return param;
    }
}
