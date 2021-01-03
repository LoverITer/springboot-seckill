package top.easyblog.seckill.cache.sync;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Huang Xin
 * @Description 线程安全的LinkedHashMap
 * @data 2021/01/03 20:49
 */
public class ConcurrentLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
    private static final long serialVersionUID = -4407809689385629881L;

    private int maxCapacity;

    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private static final int INITIAL_CAPACITY = 16;

    private final Lock lock = new ReentrantLock();

    public ConcurrentLinkedHashMap(int maxCapacity) {
        this(INITIAL_CAPACITY,maxCapacity,  DEFAULT_LOAD_FACTOR, true);
    }

    private ConcurrentLinkedHashMap( int initialCapacity, int maxCapacity,float loadFactor, boolean accessOrder) {
        super(initialCapacity, loadFactor, accessOrder);
        this.maxCapacity = maxCapacity;
    }

    @Override
    public V get(Object key) {
        lock.lock();
        try {
            return super.get(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V put(K key, V value) {
        lock.lock();
        try {
            return super.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V remove(Object key) {
        lock.lock();
        try {
            return super.remove(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxCapacity;
    }
}
