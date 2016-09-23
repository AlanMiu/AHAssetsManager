package com.autohome.ahkit.assets;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Alan Miu on 15/9/10.
 */
public class NetworkManager {
    // 最大下载线程数
    private int mMaxThreadCount = 4;
    // 当前下载线程数
    private AtomicInteger mCurrentThreadNum = new AtomicInteger(0);
    // 下载任务队列
    private PriorityBlockingQueue<HttpRequest> mQueue = new PriorityBlockingQueue<>();

    /**
     * 添加Http请求
     *
     * @param request Http请求
     */
    public void addHttpRequest(HttpRequest request) {
        mQueue.offer(request);
        // 是否创建线程
        if (mCurrentThreadNum.get() < mMaxThreadCount) {
            // 当前线程数 +1
            mCurrentThreadNum.getAndIncrement();
            new Thread() {
                public void run() {
                    while (!mQueue.isEmpty()) {
                        HttpRequest task = mQueue.poll();
                        if (task != null) task.start();
                    }
                    // 当前线程数 -1
                    mCurrentThreadNum.getAndDecrement();
                }
            }.start();
        }
    }

    public int getMaxThreadCount() {
        return mMaxThreadCount;
    }

    public void setMaxThreadCount(int maxThreadCount) {
        mMaxThreadCount = maxThreadCount;
    }

}
