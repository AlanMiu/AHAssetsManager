package com.autohome.ahkit.assets;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * Created by Alan Miu on 15/8/13.
 */
public class AssetsManager {
    // 字符串编码
    private static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");
    // 主配置文件名称
    private static final String ASSET_MAIN_CONF_NAME = "main.cnf";
    // 资源配置信息根节点名称
    private static final String ASSET_ROOT_NODE_NAME = "assets";
    // 资源版本保存目录名称
    private static final String ASSET_VERSION_NAME = ".ver";
    // 系统的版本号
    private static final String APP_ASSET_VERSION_NAME = "AppAssetVersionName";
    // 只获取一次配置目录文件信息
    private static boolean isGetFilesSize = true;
    // 缓存读取的配置文件
    private static HashMap<String, String> mConfigValues;
    // 更新读取主配置文件是否成功（不包含读取后的更新资源）
    private boolean isUpdateMainConfigLoadComplate = false;

    // 上下文对象
    private Context mContext;
    // 资源目录链接
    private String mAssetDirUrl;
    // 存储资源路径
    private String mStoragePath;
    // 内置资源目录
    private String mBundleDir;

    // 资源信息列表
    private List<AssetInfoModel> mAssetInfoList;

    // 资源下载
    private NetworkManager mDownloadManager;

    private AssetsManagerListener mAssetsListener;

    private static AssetsManager mAssetsManager = null;

    public AssetsManager(Context context, String assetDirUrl, String storagePath, String bundleDir) {
        init(context, assetDirUrl, storagePath, bundleDir, null);
    }

    public AssetsManager(Context context, String assetDirUrl, String storagePath, String bundleDir, AssetsManagerListener listener) {
        init(context, assetDirUrl, storagePath, bundleDir, listener);
    }

    private void init(Context context, String assetDirUrl, String storagePath, String bundleDir, AssetsManagerListener listener) {
        mContext = context;
        mAssetDirUrl = assetDirUrl;
        mStoragePath = storagePath;
        mBundleDir = bundleDir;
        mAssetsListener = listener;

        // 初始化下载管理类
        mDownloadManager = new NetworkManager();
        mDownloadManager.setMaxThreadCount(1);

        // 加载主配置文件
        loadMainConfig();
        // 更新主配置文件
        updateMainConfig();
    }

    /**
     * 获得应用版本名称
     *
     * @param context
     */
    private String getAppVersionName(Context context) {
        String versionName = "";
        if (context != null) {
            try {
                versionName = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                versionName = "";
                e.printStackTrace();
            }
        }
        return versionName;
    }

    public String getStoragePath() {
        return mStoragePath;
    }

    /**
     * 获取配置信息
     *
     * @param name 文件名
     * @return 配置信息 或 空
     */
    public String getConfig(String name) {
        if (mConfigValues != null) {
            String value = mConfigValues.get(name);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        boolean isGetConfigSuccess = false;
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        String json = null;
        synchronized (this) {
            String assetPath = getAssetPath(name, null);
            if (!TextUtils.isEmpty(assetPath)) {

                byte[] data = FileUtils.read(assetPath);
                if (data != null) {
                    isGetConfigSuccess = true;
                    json = new String(data, CHARSET_UTF8);
                }
            }
            // 临时处理空指针问题
            if (json == null && isFileInAssets(name)) {
                if (isGetFilesSize) {
                    isGetFilesSize = false;
                    File[] fileTemps = new File(mStoragePath).listFiles();
                }
                if (isFileInAssets(name)) {
                    InputStream in = null;
                    try {
                        in = mContext.getAssets().open(mBundleDir + "/" + name);
                        //获取文件的字节数
                        int lenght = in.available();
                        //创建byte数组
                        byte[] buffer = new byte[lenght];
                        in.read(buffer);
                        json = new String(buffer, CHARSET_UTF8);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (mAssetsListener != null) {
            mAssetsListener.onGetConfigStatus(isGetConfigSuccess);
        }
        if (mConfigValues == null) {
            mConfigValues = new HashMap<>();
        }
        mConfigValues.put(name, json);
        return json;
    }

    /**
     * 是否最新版本
     *
     * @param zip
     * @return
     */
    public boolean getLatestVersion(String zip) {
        if (!isUpdateMainConfigLoadComplate || mAssetInfoList == null) {
            return false;
        }
        for (AssetInfoModel assetInfo : mAssetInfoList) {
            if (zip.equals(assetInfo.zip)) {
                if (!TextUtils.isEmpty(assetInfo.ver) && assetInfo.ver.equals(readAssetVersion(zip))) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 资源名称是否在main.cnf中存在
     *
     * @param name 资源name
     * @return
     */
    public boolean isNameInAssets(String name) {
        if ( mAssetInfoList == null || TextUtils.isEmpty(name)) {
            return false;
        }
        for (AssetInfoModel assetInfo : mAssetInfoList) {
            if (name.equals(assetInfo.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取 图片文件
     *
     * @param name
     * @return
     */
    public Bitmap getAssetBitmap(String name) {
        String pathString = getAssetPath(name);
        if (!TextUtils.isEmpty(pathString)) {
            Bitmap bitmap = null;
            File file = new File(pathString);
            if (file.exists()) {
                bitmap = BitmapFactory.decodeFile(pathString);
            }
            return bitmap;
        }
        return null;
    }

    /**
     * 获取资源路径
     *
     * @param name 文件名
     * @return 资源路径 或 空
     */
    public String getAssetPath(String name) {
        if (TextUtils.isEmpty(mStoragePath) || TextUtils.isEmpty(name)) return null;

        String assetPath = null;
        synchronized (this) {
            String path = mStoragePath + "/" + name;
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                assetPath = path;
            } else if (file.exists() && !name.contains(".")) {
                assetPath = path;
            }

        }

        return assetPath;
    }

    public String getAssetPath(String name, String nulls) {
        if (TextUtils.isEmpty(mStoragePath) || TextUtils.isEmpty(name)) return null;

        String assetPath = null;
        synchronized (this) {
            String path = mStoragePath + "/" + name;
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                assetPath = path;
            } else if (file.exists() && !name.contains(".")) {
                assetPath = path;
            }
        }

        return assetPath;
    }

    /**
     * 加载主配置文件
     */
    private void loadMainConfig() {
        if (TextUtils.isEmpty(mStoragePath) || TextUtils.isEmpty(mBundleDir)) {
            return;
        }

        synchronized (this) {

            //当前应用版本名称
            String appVersionName = getAppVersionName(mContext);
            //本地缓存asset文件的应用版本名称
            String AppLocalConfigVersionName = readAssetVersion(APP_ASSET_VERSION_NAME);
            if (!appVersionName.equals(AppLocalConfigVersionName)) {
                // 如果存储目录中存在主配置文件, 直接加载
                File storagefile = new File(mStoragePath);
                if (storagefile.exists()) {
                    deleteDir(storagefile);
                }
            }

            // 主配置文件存储路径
            String mainConfigStoragePath = mStoragePath + "/" + ASSET_MAIN_CONF_NAME;
            // 如果存储目录中存在主配置文件, 直接加载
            File file = new File(mainConfigStoragePath);
            if (file.exists() && file.isFile()) {
                mAssetInfoList = getAssetInfoList(mainConfigStoragePath);
                // 如果主配置文件加载异常, 删除并重新加载
                if (mAssetInfoList == null || mAssetInfoList.size() == 0) {
                    file.delete();
                    loadMainConfig();
                }
            }
            // 否则先把内置的主配置文件复制到存储目录, 再进行加载
            else {
                if (isFileInAssets(ASSET_MAIN_CONF_NAME)) {
                    try {
                        // 加载内置主配置文件
                        InputStream isMainConfig = mContext.getAssets().open(mBundleDir + "/" + ASSET_MAIN_CONF_NAME);
                        byte[] mainConfigBundleData = FileUtils.read(isMainConfig);
                        if (mainConfigBundleData != null && mainConfigBundleData.length > 0) {
                            mAssetInfoList = getAssetInfoList(mainConfigBundleData);
                            // 内置主配置文件复制到储存目录
                            FileUtils.write(mainConfigStoragePath, mainConfigBundleData);
                            saveAssetVersion(APP_ASSET_VERSION_NAME, appVersionName);//保存资源的版本名称
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }

            // 校验资源文件
            if (mAssetInfoList != null || mAssetInfoList.size() > 0) {
                for (AssetInfoModel assetInfo : mAssetInfoList) {
                    // 资源路径
                    String assetPath = getAssetPath(assetInfo.name);
                    // 是否需要从内置资源中复制到存储目录
                    boolean isNeedCopy = false;
                    if (TextUtils.isEmpty(assetPath)) {
                        isNeedCopy = true;
                    } else {
                        File assetFile = new File(assetPath);
                        // 资源不存在 or 资源类型异常(资源只能是文件)
                        if (!assetFile.exists() || (!file.isFile() && assetInfo.name.contains(".")))
                            isNeedCopy = true;
                    }
                    // 需要复制资源
                    if (isNeedCopy && isFileInAssets(assetInfo.zip)) {
                        try {
                            InputStream isAssetInfo = mContext.getAssets().open(mBundleDir + "/" + assetInfo.zip);
                            boolean isSuccess = safeUnzipFile(isAssetInfo, mStoragePath);
                            // 资源复制成功, 保存资源版本
                            if (isSuccess) {
                                boolean isok = saveAssetVersion(assetInfo.zip, assetInfo.ver);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * 更新主配置
     */
    private void updateMainConfig() {
        if (TextUtils.isEmpty(mAssetDirUrl) || TextUtils.isEmpty(mStoragePath)) return;

        // 主配置文件链接
        String url = mAssetDirUrl + "/" + ASSET_MAIN_CONF_NAME;
        // 开始下载
        HttpRequest request = new HttpRequest(url, null, HttpRequest.Method.GET, new HttpRequest.OnHttpRequestListener() {
            @Override
            public void onSuccess(HttpRequest connection, final byte[] response) {
                // 在子线程中更新主配置文件、重新加载主配置、更新资源文件
                new Thread() {
                    @Override
                    public void run() {
                        // 校验主配置文件
                        JSONObject json = null;
                        try {
                            json = new JSONObject(new String(response, CHARSET_UTF8));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        if (json != null && json.length() > 0) {
                            // 主配置文件存储路径
                            String mainConfigStoragePath = mStoragePath + "/" + ASSET_MAIN_CONF_NAME;
                            // 保存主配置文件
                            boolean isSuccess = false;
                            synchronized (this) {
                                isSuccess = FileUtils.write(mainConfigStoragePath, response);
                            }
                            if (isSuccess) {
                                // 主配置文件更新成功后重新载入
                                loadMainConfig();
                                isUpdateMainConfigLoadComplate = true;
                                // 更新资源文件
                                updateAssetsFile();
                            }
                        }
                    }
                }.start();
            }

            @Override
            public void onFailure(HttpRequest connection, Exception exception) {
                // 主配置文件下载失败
            }
        });
        mDownloadManager.addHttpRequest(request);
    }

    private static String[] mAssetsList;

    /**
     * 判断文件是否存在与assets文件下
     *
     * @return
     */
    private boolean isFileInAssets(String name) {
        if (TextUtils.isEmpty(name) && mContext == null) {
            return false;
        }
        if (mAssetsList == null) {
            try {
                mAssetsList = mContext.getAssets().list(mBundleDir);

            } catch (IOException e) {

            }
        }
        for (String na : mAssetsList) {
            if (na.contains(name)) {
                return true;
            }
        }
        return false;

    }

    /**
     * 更新资源文件
     */
    private void updateAssetsFile() {
        if (TextUtils.isEmpty(mAssetDirUrl) || TextUtils.isEmpty(mStoragePath) || mAssetInfoList == null || mAssetInfoList.size() == 0)
            return;

        for (AssetInfoModel assetInfo : mAssetInfoList) {
            final String zip = assetInfo.zip;
            final String ver = assetInfo.ver;
            final String name = assetInfo.name;
            if (!TextUtils.isEmpty(zip) && !TextUtils.isEmpty(ver) && !TextUtils.isEmpty(name)) {
                boolean isNeedUpdate = false;
                // 资源路径
                String assetPath = getAssetPath(name);
                // 是否需要更新
                if (TextUtils.isEmpty(assetPath)) {
                    isNeedUpdate = true;
                } else {
                    File file = new File(assetPath);
                    // 资源不存在 or 资源类型异常(资源只能是文件) or 资源版本与存储的资源版本不一致, 进行更新
                    if (!file.exists() || (!file.isFile() && name.contains(".")) || !ver.equals(readAssetVersion(zip)))
                        isNeedUpdate = true;
                }
                // 更新资源
                if (isNeedUpdate) {
                    //如果文件需要更新则移除对应缓存内容
                    if (mConfigValues != null && mConfigValues.containsKey(name)) {
                        mConfigValues.remove(name);
                    }
                    // 资源链接
                    String url = mAssetDirUrl + "/" + zip;
                    // 压缩包的保存路径
                    String fileName = keyWithString(url);
                    final String zipFilePath = mStoragePath + "/" + fileName;
                    // 开始下载
                    HttpRequest request = new HttpRequest(url, null, HttpRequest.Method.GET, new HttpRequest.OnHttpRequestListener() {
                        @Override
                        public void onSuccess(HttpRequest connection, byte[] response) {
                            new Thread() {
                                @Override
                                public void run() {
                                    synchronized (this) {
                                        // 资源下载成功, 解压并更新
                                        boolean isSuccess = safeUnzipFile(zipFilePath, mStoragePath);
                                        // 资源更新成功, 保存资源版本
                                        if (isSuccess) {
                                            saveAssetVersion(zip, ver);
                                        }
                                        if (mAssetsListener != null) {
                                            mAssetsListener.onDataUpdateStatus(name, isSuccess);
                                        }
                                    }
                                }
                            }.start();
                        }

                        @Override
                        public void onFailure(HttpRequest connection, Exception exception) {
                            // 资源下载失败
                        }
                    });
                    request.setOutputPath(zipFilePath);
                    mDownloadManager.addHttpRequest(request);
                }
            }
        }
    }


    /**
     * 安全解压文件
     *
     * @param zipPath 压缩文件路径
     * @param outPath 解压目录路径
     * @return 是否成功
     */
    private boolean safeUnzipFile(String zipPath, String outPath) {
        if (TextUtils.isEmpty(zipPath) || TextUtils.isEmpty(outPath)) return false;

        // 临时解压目录, 使用时间戳命名
        String tmpOutPath = outPath + "/tmp_unzip_" + System.currentTimeMillis();
        boolean isSuccess = FileUtils.unzip(zipPath, tmpOutPath);
        // 解压成功, 移动文件到解压目录下
        if (isSuccess) isSuccess = copyAsset(tmpOutPath, outPath, true);
        // 删除压缩包
        new File(zipPath).delete();
        // 删除临时目录
        new File(tmpOutPath).delete();

        return isSuccess;
    }

    /**
     * 安全解压文件
     *
     * @param zipStream 压缩文件流
     * @param outPath   解压目录路径
     * @return 是否成功
     */
    private boolean safeUnzipFile(InputStream zipStream, String outPath) {
        if (zipStream == null || TextUtils.isEmpty(outPath)) return false;

        // 临时解压目录, 使用时间戳命名
        String tmpOutPath = outPath + "/tmp_unzip_" + System.currentTimeMillis();
        boolean isSuccess = FileUtils.unzip(zipStream, tmpOutPath);
        // 解压成功, 移动文件到解压目录下
        if (isSuccess) isSuccess = copyAsset(tmpOutPath, outPath, true);
        // 删除临时目录
        new File(tmpOutPath).delete();

        return isSuccess;
    }

    /**
     * 复制资源文件或文件夹
     *
     * @param srcPath     源路径
     * @param dstPath     目标路径
     * @param isDeleteSrc 是否删除源文件
     * @return 是否成功
     */
    private boolean copyAsset(String srcPath, String dstPath, boolean isDeleteSrc) {
        if (TextUtils.isEmpty(srcPath) || TextUtils.isEmpty(dstPath)) return false;

        File srcFile = new File(srcPath);
        if (srcFile.exists()) {
            // 复制文件夹
            if (srcFile.isDirectory()) {
                // 获取文件夹下所有子文件名
                File[] files = srcFile.listFiles();
                for (File file : files) {
                    boolean isSuccess = copyAsset(file.getAbsolutePath(), dstPath + "/" + file.getName(), isDeleteSrc);
                    if (!isSuccess) return false;
                }
                // 是否需要删除目录
                if (isDeleteSrc) srcFile.delete();
                // 复制子文件全部成功
                return true;
            }
            // 复制文件
            else {
                // 如果目标路径已存在, 先删除
                File dstFile = new File(dstPath);
                if (dstFile.exists()) dstFile.delete();

                // 目标路径的目录
                File dstDir = dstFile.getParentFile();
                // 如果目录不存在, 先创建
                if (!dstDir.exists()) dstDir.mkdirs();
                // 如果目录被文件占用, 先删除再创建
                if (dstDir.exists() && dstDir.isFile()) {
                    dstFile.delete();
                    dstFile.mkdirs();
                }

                // 删除源文件使用move, 保留源文件使用copy
                if (isDeleteSrc) return srcFile.renameTo(dstFile);
                else return FileUtils.copyFile(srcFile, dstFile);
            }
        }

        return false;
    }

    /**
     * 保存资源版本
     *
     * @param zip     资源包名
     * @param version 资源版本
     * @return 是否成功
     */
    private boolean saveAssetVersion(String zip, String version) {
        if (TextUtils.isEmpty(mStoragePath)) return false;

        // 资源版本保存目录
        String assetVersionDirPath = mStoragePath + "/" + ASSET_VERSION_NAME;

        File versionDir = new File(assetVersionDirPath);
        // 目录类型异常清除后重新创建
        if (versionDir.exists() && versionDir.isFile()) {
            versionDir.delete();
            versionDir.mkdirs();
        }
        // 创建目录
        if (!versionDir.exists()) versionDir.mkdirs();

        // 资源版本存放路径 .../.ver/asset.zip.ver
        String assetVersionFilePath = assetVersionDirPath + "/" + zip + ASSET_VERSION_NAME;

        return FileUtils.write(assetVersionFilePath, version.getBytes(CHARSET_UTF8));
    }

    /**
     * 读取资源版本
     *
     * @param zip 资源包名
     * @return 资源版本
     */
    private String readAssetVersion(String zip) {
        if (TextUtils.isEmpty(mStoragePath)) return null;

        // 资源版本存放路径 .../.ver/asset.zip.ver
        String assetVersionFilePath = mStoragePath + "/" + ASSET_VERSION_NAME + "/" + zip + ASSET_VERSION_NAME;

        byte[] bytes = FileUtils.read(assetVersionFilePath);
        if (bytes == null || bytes.length == 0) return null;
        else return new String(bytes, CHARSET_UTF8);
    }

    /**
     * 获取资源信息列表
     *
     * @param path 主配置文件路径
     * @return 资源列表 或 空
     */
    private List<AssetInfoModel> getAssetInfoList(String path) {
        // 判断主配置文件是否存在
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            byte[] data = FileUtils.read(path);
            return getAssetInfoList(data);
        }

        return null;
    }

    /**
     * 初始化资源信息列表
     *
     * @param data 主配置数据
     * @return 资源列表 或 空
     */
    private List<AssetInfoModel> getAssetInfoList(byte[] data) {
        if (data != null && data.length > 0) {
            try {
                JSONObject json = new JSONObject(new String(data, CHARSET_UTF8));
                JSONArray jsonAssetInfoList = json.optJSONArray(ASSET_ROOT_NODE_NAME);
                List<AssetInfoModel> assetInfoList = new ArrayList<>();
                for (int i = 0; i < jsonAssetInfoList.length(); i++) {
                    AssetInfoModel mAssetInfo = new AssetInfoModel(jsonAssetInfoList.optJSONObject(i));
                    assetInfoList.add(mAssetInfo);
                }
                return assetInfoList;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * 递归删除目录下的所有文件及子目录下所有文件
     *
     * @param dir 将要删除的文件目录
     * @return boolean Returns "true" if all deletions were successful.
     * If a deletion fails, the method stops attempting to
     * delete and returns "false".
     */
    private boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            //递归删除目录中的子目录下
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        // 目录此时为空，可以删除
        return dir.delete();
    }

    /**
     * 字符串生成key
     *
     * @param str 字符串
     * @return key 或 空
     */
    private String keyWithString(String str) {
        if (str != null && str.length() > 0) {
            int halfLength = str.length() / 2;
            return str.substring(0, halfLength).hashCode() + "" + str.substring(halfLength, str.length()).hashCode();
        }
        return null;
    }

    /**
     * 资源信息
     */
    private class AssetInfoModel {
        private String name;
        private String ver;
        private String zip;

        public AssetInfoModel(JSONObject json) {
            if (json != null) {
                name = json.optString("name", null);
                ver = json.optString("ver", null);
                zip = json.optString("zip", null);
            }
        }
    }

    public interface AssetsManagerListener {
        /**
         * 错误日志收集
         *
         * @param data
         */
        public void onErrorDataReady(String data);

        /**
         * 获取配置文件成功失败状态
         *
         * @param success
         */
        public void onGetConfigStatus(boolean success);

        /**
         * 配置文件下载更新成功失败
         *
         * @param name    配置文件名称
         * @param success
         */
        public void onDataUpdateStatus(String name, boolean success);
    }

}
