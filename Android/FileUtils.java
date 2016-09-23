package com.autohome.ahkit.assets;

import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by Alan Miu on 15/12/3.
 */
public class FileUtils {

    /**
     * 读取文件数据
     * @param path 文件路径
     * @return 文件数据 或 空
     */
    public static byte[] read(String path) {
        if (TextUtils.isEmpty(path)) return null;

        try {
            return read(new FileInputStream(path));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 读取文件数据
     * @param inStream 输入流
     * @return 文件数据 或 空
     */
    public static byte[] read(InputStream inStream) {
        if (inStream == null) return null;

        byte[] data = null;
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(inStream);
            data = new byte[bis.available()];
            bis.read(data);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return data;
    }

    /**
     * 数据写入文件
     * @param path 文件路径
     * @param data 数据
     * @return 是否成功
     */
    public static boolean write(String path, byte[] data) {
        if (data == null || data.length == 0) return false;

        // 路径中包含文件夹并且不存在时创建
        File dir = new File(path).getParentFile();
        if (dir != null && !dir.exists())
            dir.mkdirs();

        BufferedOutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(path));
            out.write(data);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return false;
    }

    /**
     * 复制文件
     * @param srcFile 源文件
     * @param dstFile 目标文件
     * @return
     */
    public static boolean copyFile(File srcFile, File dstFile) {
        if (srcFile == null || !srcFile.exists() || dstFile == null)
            return false;

        if (dstFile.exists())
            dstFile.delete();

        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            dstFile.createNewFile();
            fis = new FileInputStream(srcFile);
            fos = new FileOutputStream(dstFile);

            int index;
            byte[] buffer = new byte[1024];
            while ((index = fis.read(buffer, 0, buffer.length)) > 0) {
                fos.write(buffer, 0, index);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null)
                    fos.close();
                if (fis != null)
                    fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 压缩
     * @return 是否成功
     */
    public static boolean zip(String inPath, String zipPath) {
        // TODO
        return false;
    }

    /**
     * 解压文件到同一目录下
     * @param zipPath 压缩包路径
     * @return
     */
    public static boolean unzip(String zipPath) {
        if (TextUtils.isEmpty(zipPath)) return false;

        return unzip(zipPath, new File(zipPath).getParent());
    }

    /**
     * 解压文件到指定路径下
     *
     * @param zipPath 压缩包路径
     * @param outPath 解压路径
     * @return 是否成功
     */
    public static boolean unzip(String zipPath, String outPath) {
        if (TextUtils.isEmpty(zipPath) || TextUtils.isEmpty(outPath)) return false;

        // 压缩文件流
        FileInputStream inStream = null;
        try {
            inStream = new FileInputStream(zipPath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return unzip(inStream, outPath);
    }

    /**
     * 解压文件到指定目录下
     * @param inStream  压缩文件流
     * @param outPath   解压路径
     * @return 是否成功
     */
    public static boolean unzip(InputStream inStream, String outPath) {
        if (inStream == null || TextUtils.isEmpty(outPath)) return false;

        // 解压目录
        File outDir = new File(outPath);
        if (!outDir.exists()) {
            boolean isSucceed = outDir.mkdirs();
            if (!isSucceed) return false;
        }

        // 开始解压
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(inStream);
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                // 目录
                if (zipEntry.isDirectory()) {
                    File dir = new File(outDir.getPath() + File.separator + zipEntry.getName());
                    if (!dir.exists()) {
                        boolean isSucceed = dir.mkdirs();
                        if (!isSucceed) return false;
                    }
                }
                // 文件
                else {
                    File file = new File(outDir.getPath() + File.separator + zipEntry.getName());
                    boolean isSucceed = file.createNewFile();
                    if (!isSucceed) return false;

                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(file);
                        int len;
                        byte[] buffer = new byte[1024];
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    } finally {
                        if (fos != null) {
                            try {
                                fos.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (zis != null) {
                try {
                    zis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // 无异常, 解压成功
        return true;
    }

}
