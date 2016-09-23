package com.autohome.ahkit.assets;

import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Alan Miu on 15/12/3.
 */
public class HttpRequest implements Comparable<HttpRequest> {
    // 序号
    private static AtomicInteger COUNTER = new AtomicInteger(0);
    private int mSerial = COUNTER.getAndIncrement();

    // 队列优先级
    private Priority mPriority = Priority.NORMAL;

    // 链接
    private String mUrl;
    // 请求头
    private Map<String, String> mHeaders;
    // 请求参数
    private Map<String, String> mParams;
    // 请求方法
    private Method mMethod = Method.GET;

    // 指定上传文件路径
    private String mInputPath;
    // 保存下载文件路径
    private String mOutputPath;

    // 请求超时时间
    private int mTimeOut = 30 * 1000;
    // 请求监听事件
    private OnHttpRequestListener mOnHttpRequestListener;

//    // 是否开启断点续传
//    private boolean isEnableBreakpointContinuingly = false;

//    // 下载总长度
//    private int mTotalLength;

    public HttpRequest(String url, Map<String, String> params, Method method, OnHttpRequestListener listener) {
        this(url, null, params, method, listener);
    }

    public HttpRequest(String url, Map<String, String> headers, Map<String, String> params, Method method, OnHttpRequestListener listener) {
        mUrl = url;
        mHeaders = headers;
        mParams = params;
        mMethod = method;
        mOnHttpRequestListener = listener;
    }

    public void start() {
        // 开始连接
        if (mOnHttpRequestListener != null) mOnHttpRequestListener.onStart(this);

        // 解析Url
        URL url = null;
        try {
            url = new URL(mUrl);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        // 校验URL
        if (url == null) {
            if (mOnHttpRequestListener != null)
                mOnHttpRequestListener.onFailure(this, new Exception("Url is invalid"));
            return;
        }

        // 有指定数据输出路径, 使用文件接收, 否则使用内存接收
        OutputStream os = null;
        if (TextUtils.isEmpty(mOutputPath)) {
            os = new ByteArrayOutputStream();
        } else {
            try {
                File file = new File(mOutputPath);
                // 路径被目录占用, 删除
                if (file.exists() && file.isDirectory()) file.delete();
                // 目录被文件占用, 删除
                File dir = file.getParentFile();
                if (dir.exists() && dir.isFile()) dir.delete();
                // 目录不存在, 创建
                if (!dir.exists()) dir.mkdirs();
                os = new FileOutputStream(mOutputPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                if (mOnHttpRequestListener != null)
                    mOnHttpRequestListener.onFailure(this, new Exception("Output path is invalid"));
                return;
            }
        }

        // 连接下载
        boolean isSucceed = false;
        HttpURLConnection conn = null;
        try {
            // 设置通用参数
            conn = (HttpURLConnection) url.openConnection();
//            conn.setRequestProperty("accept", "*/*");
//            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setRequestProperty("user-agent", "AHNetwork(Android)");


            // 接收数据
            int length, progress = 0;
            byte[] buffer = new byte[1024];
            InputStream input = conn.getInputStream();
            // 数据总长度
            int totalLength = conn.getContentLength();
            while ((length = input.read(buffer, 0, buffer.length)) > 0) {
                os.write(buffer, 0, length);
                progress += length;
                if (mOnHttpRequestListener != null)
                    mOnHttpRequestListener.onReceive(this, length, progress, totalLength);
            }

            if (mOnHttpRequestListener != null) {
                // 校验数据大小
                if (totalLength == progress) {
                    byte[] data = null;
                    if (os instanceof ByteArrayOutputStream)
                        data = ((ByteArrayOutputStream) os).toByteArray();
                    mOnHttpRequestListener.onSuccess(this, data);
                } else {
                    mOnHttpRequestListener.onFailure(this, new Exception("Data receive exception"));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public int compareTo(HttpRequest another) {
        // 优先级高先出. 优先级相同时, 序号小先出
        return mPriority.ordinal() > another.mPriority.ordinal() ? -1 : (mPriority.ordinal() < another.mPriority.ordinal() ? 1 : (mSerial > another.mSerial ? 1 : -1));
    }

    public String getOutputPath() {
        return mOutputPath;
    }

    public void setOutputPath(String outputPath) {
        mOutputPath = outputPath;
    }

    /**
     * 请求监听事件
     */
    public static abstract class OnHttpRequestListener {
        public void onStart(HttpRequest connection) {
        }

        public void onSend(HttpRequest connection, int length, int progress, int total) {
        }

        public void onReceive(HttpRequest connection, int length, int progress, int total) {
        }

        public abstract void onSuccess(HttpRequest connection, byte[] response);

        public abstract void onFailure(HttpRequest connection, Exception exception);
    }

    public enum Method {
        GET(0), POST(1);

        private int value;
        private Method (int value) {
            this.value = value;
        }

        public String string() {
            switch (value) {
                case 0:
                    return "GET";
                case 1:
                    return "POST";
                default:
                    return null;
            }
        }
    }

    public enum Priority {
        VERYLOW, LOW, NORMAL, HIGH, VERYHIGH
    }

}