package top.easyblog.seckill.cache.sync;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 用于广播的缓存消息格式
 *
 * @author Huang Xin
 * @date 2020/4/28 20:29
 */
@Getter
@Setter
@Accessors(chain = true)
public class CacheMessage implements Serializable {

    private static final long serialVersionUID = -1L;

    /**
     * 缓存消息id,幂等性处理消息的关键。给每个消息一个全局唯一的ID
     */
    private String instanceId;


    /**
     * 缓存名称，就是缓存实例的唯一标识ID
     */
    private String cacheName;

    /**
     * 缓存消息的操作 refresh/clear
     */
    private String optType;

    /**
     * 缓存key
     */
    private String key;


    /**
     * 缓存的内容
     */
    private JSON value;


    /**
     * 消息的状态
     * -1 表示消息异常
     * 1  表示消息已经消费了
     * 0  消息还未消费
     */
    private int status;


    public static final int CACHE_MESSAGE_EXCEPTION =-1;

    public static final int CACHE_MESSAGE_CONSUMED_YET =1;

    public static final int CACHE_MESSAGE_NOT_CONSUME=0;

    public CacheMessage() {

    }

    public CacheMessage(String instanceId, String cacheName, String key, String optType) {
        this.instanceId = instanceId;
        this.cacheName = cacheName;
        this.key = key;
        this.optType = optType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", instanceId=").append(instanceId);
        sb.append(", cacheName=").append(cacheName);
        sb.append(", optType=").append(optType);
        sb.append(", key=").append(key);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }

}
