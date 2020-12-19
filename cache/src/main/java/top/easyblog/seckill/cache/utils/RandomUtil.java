package top.easyblog.seckill.cache.utils;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Huang Xin
 * @date 2020/7/2 14:08
 */
public class RandomUtil {

    public static int getRandomInt(int min, int max) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return random.nextInt(max) % (max - min + 1) + min;
    }

    public static String getUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

}
