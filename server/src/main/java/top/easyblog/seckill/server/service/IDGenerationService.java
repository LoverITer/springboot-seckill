package top.easyblog.seckill.server.service;

import org.springframework.stereotype.Service;
import top.easyblog.seckill.api.utils.IDWorker;

/**
 * 基于分布式ID算法——雪花算法实现的ID生成服务
 *
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/09 21:08
 */
@Service
public class IDGenerationService {

    /**
     * 雪花算法实现的额分布ID生成器
     */
    private IDWorker idWorker = new IDWorker(2, 3);


    public long nextId() {
        return idWorker.nextId();
    }
}
