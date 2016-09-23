//
//  AHAssetsManager.m
//  AHKit
//
//  Created by Alan Miu on 15/9/17.
//  Copyright (c) 2015年 AutoHome. All rights reserved.
//

#import "AHAssetsManager.h"
#import "AHNetworkManager.h"
#import "AHHttpRequest.h"
#import "SSZipArchive.h"

#define ASSET_MAIN_CONF_NAME    @"main.cnf"
#define ASSET_ROOT_NODE_NAME    @"assets"
#define ASSET_VERSION_NAME      @".ver"

@implementation AHAssetsManager {
    NSRecursiveLock *_lock;
    AHNetworkManager *_downloadManager;
}

static AHAssetsManager *assetsManager = nil;
+ (instancetype)sharedManager:(NSString *)assetDirUrl storagePath:(NSString *)storagePath bundlePath:(NSString *)bundlePath {
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        assetsManager = [[self alloc] initWithUrl:assetDirUrl storagePath:storagePath bundlePath:bundlePath];
    });
    return assetsManager;
}

+ (instancetype)sharedManager {
    return assetsManager;
}

- (instancetype)init {
    NSAssert(NO, @"Please use initWithUrl:");
    return nil;
}

#pragma mark Public Methods

- (instancetype)initWithUrl:(NSString *)assetDirUrl storagePath:(NSString *)storagePath bundlePath:(NSString *)bundlePath {
    self = [super init];
    if (self) {
        if (assetDirUrl.length > 0 && storagePath.length > 0) {
            _assetDirUrl = assetDirUrl;
            _storagePath = storagePath;
            _bundlePath = bundlePath;
            
            // 初始化同步锁
            _lock = [[NSRecursiveLock alloc] init];
            _lock.name = NSStringFromClass(self.class);
            
            // 初始化下载管理类
            _downloadManager = [[AHNetworkManager alloc] init];
            _downloadManager.maxThreadCount = 1;
            
            // 加载主配置文件
            [self loadMainConfig];
            // 更新主配置文件
            [self updateMainConfig];
        }
    }
    return self;
}

/**
 *  获取配置信息
 *
 *  @param name 文件名
 *
 *  @return 配置信息 或 空
 */
- (NSString *)configForName:(NSString *)name {
    if (name.length == 0)
        return nil;
    
    NSString *json = nil;
    NSString *assetPath = [self assetPathForName:name];
    if (assetPath.length > 0) {
        [_lock lock];
        NSData *data = [NSData dataWithContentsOfFile:assetPath];
        if (data)
            json = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
        [_lock unlock];
    }
    
    return json;
}

/**
 *  获取资源路径
 *
 *  @param name 文件名
 *
 *  @return 资源路径 或 空
 */
- (NSString *)assetPathForName:(NSString *)name {
    if (_storagePath.length == 0 || name.length == 0)
        return nil;
    
    [_lock lock];
    NSString *assetPath = [_storagePath stringByAppendingPathComponent:name];
    BOOL isDirectory;
    BOOL isExists = [[NSFileManager defaultManager] fileExistsAtPath:assetPath isDirectory:&isDirectory];
    [_lock unlock];
    
    if (isExists && !isDirectory)
        return assetPath;
    
    return nil;
}

#pragma mark Private Methods

/**
 * 加载主配置文件
 */
- (void)loadMainConfig {
    if (_storagePath.length == 0 || _bundlePath.length == 0)
        return;
    
    [_lock lock];
    // 主配置文件存储路径
    NSString *mainConfigStoragePath = [_storagePath stringByAppendingPathComponent:ASSET_MAIN_CONF_NAME];
    
    BOOL isDirectory;
    BOOL isExists = [[NSFileManager defaultManager] fileExistsAtPath:mainConfigStoragePath isDirectory:&isDirectory];

    // 如果存储目录中存在主配置文件, 直接加载
    if (isExists && !isDirectory) {
        _assetInfoList = [self assetInfoListWhitPath:mainConfigStoragePath];
        // 如果主配置文件加载异常, 删除并重新加载
        if (_assetInfoList.count == 0) {
            [[NSFileManager defaultManager] removeItemAtPath:mainConfigStoragePath error:nil];
            [self loadMainConfig];
        }
    }
    // 主配置文件不存在, 把资源从内置目录复制到存储目录
    else {
        // 加载内置主配置文件
        NSString *mainConfigBundlePath = [_bundlePath stringByAppendingPathComponent:ASSET_MAIN_CONF_NAME];
        _assetInfoList = [self assetInfoListWhitPath:mainConfigBundlePath];
        
        // 复制所有资源
        for (AHAssetInfoModel *assetInfo in _assetInfoList) {
            NSString *zipPath = [_bundlePath stringByAppendingPathComponent:assetInfo.zip];
            BOOL isSuccess = [self safeUnzipFileAtPath:zipPath outPath:_storagePath];
            // 资源复制成功, 保存资源版本
            if (isSuccess)
                [self saveAssetVersionForZip:assetInfo.zip version:assetInfo.ver];
        }
        // 复制主配置文件
        [self copyAssetAtPath:mainConfigBundlePath toPath:mainConfigStoragePath isDeleteSrc:NO];
    }
    [_lock unlock];
}

/**
 * 更新主配置
 */
- (void)updateMainConfig {
    if (_assetDirUrl.length == 0 || _storagePath.length == 0)
        return;
    // 主配置文件链接
    NSString *url = [_assetDirUrl stringByAppendingPathComponent:ASSET_MAIN_CONF_NAME];
    // 开始下载
    __weak AHAssetsManager *weakSelf = self;
    AHHttpRequest *request = [AHHttpRequest requestWhitUrl:url params:nil method:AHHttpMethodGet success:^(AHHttpRequest *request, id response) {
        // 在子线程中更新主配置文件、重新加载主配置、更新资源文件
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
            // 校验主配置文件
            NSError *error;
            NSDictionary *json = [NSJSONSerialization JSONObjectWithData:response options:0 error:&error];
            if (!error && json.count > 0) {
                // 主配置文件存储路径
                NSString *mainConfigStoragePath = [_storagePath stringByAppendingPathComponent:ASSET_MAIN_CONF_NAME];
                // 保存主配置文件
                BOOL isSuccess = [response writeToFile:mainConfigStoragePath atomically:YES];
                if (isSuccess) {
                    // 主配置文件更新成功后重新载入
                    [weakSelf loadMainConfig];
                    // 更新资源文件
                    [weakSelf updateAssetsFile];
                }
            }
        });
    } failure:^(AHHttpRequest *request, NSError *error) {
        // 主配置文件下载失败
    }];
    [_downloadManager addHttpRequest:request];
}

/**
 * 更新资源文件
 */
- (void)updateAssetsFile {
    if (_assetDirUrl.length == 0 || _storagePath.length == 0 || _assetInfoList.count == 0)
        return;

    for (AHAssetInfoModel *assetInfo in _assetInfoList) {
        NSString *zip = assetInfo.zip;
        NSString *ver = assetInfo.ver;
        NSString *name = assetInfo.name;
        if (zip.length > 0 && ver.length > 0 && name.length > 0) {
            // 资源路径
            NSString *assetPath = [self assetPathForName:name];
            BOOL isDirectory;
            BOOL isExists = [[NSFileManager defaultManager] fileExistsAtPath:assetPath isDirectory:&isDirectory];
            
            // 资源不存在 or 资源类型异常(资源只能是文件) or 资源版本与存储的资源版本不一致, 进行更新
            if (!isExists || isDirectory || ![ver isEqualToString:[self readAssetVersionForZip:zip]]) {
                // 资源链接
                NSString *url = [_assetDirUrl stringByAppendingPathComponent:zip];
                // 压缩包的保存路径
                NSString *fileName = [self keyWithString:url];
                NSString *zipFilePath = [_storagePath stringByAppendingPathComponent:fileName];
                // 开始下载
                __weak AHAssetsManager *weakSelf = self;
                AHHttpRequest *request = [AHHttpRequest requestWhitUrl:url params:nil method:AHHttpMethodGet success:^(AHHttpRequest *request, id response) {
                    // 在子线程中解压更新资源
                    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
                        [_lock lock];
                        // 资源下载成功, 解压并更新
                        BOOL isSuccess = [weakSelf safeUnzipFileAtPath:zipFilePath outPath:weakSelf.storagePath];
                        // 资源更新成功, 保存资源版本
                        if (isSuccess)
                            [self saveAssetVersionForZip:zip version:ver];
                        [_lock unlock];
                    });
                } failure:^(AHHttpRequest *request, NSError *error) {
                    // 资源下载失败
                }];
                request.outputStream = [NSOutputStream outputStreamToFileAtPath:zipFilePath append:NO];;
                [_downloadManager addHttpRequest:request];
            }
        }
    }
}

/**
 *  安全解压文件
 *
 *  @param zipPath 压缩文件路径
 *  @param outPath 解压目录路径
 *
 *  @return 是否成功
 */
- (BOOL)safeUnzipFileAtPath:(NSString *)zipPath outPath:(NSString *)outPath {
    if (zipPath.length == 0 || outPath.length == 0)
        return NO;
    
    NSFileManager *fileManager = [NSFileManager defaultManager];
    // 临时解压目录, 使用时间戳命名
    NSString *tmpOutPath = [NSString stringWithFormat:@"%@/tmp_unzip_%f", outPath, [[NSDate date] timeIntervalSince1970]];
    BOOL isSuccess = [SSZipArchive unzipFileAtPath:zipPath toDestination:tmpOutPath];
    // 解压成功, 移动文件到解压目录下
    if (isSuccess)
        isSuccess = [self copyAssetAtPath:tmpOutPath toPath:outPath isDeleteSrc:YES];
    // 删除压缩包
    [fileManager removeItemAtPath:zipPath error:nil];
    // 删除临时目录
    [fileManager removeItemAtPath:tmpOutPath error:nil];
    
    return isSuccess;
}

/**
 *  复制资源文件或文件夹
 *
 *  @param srcPath     源路径
 *  @param dstPath     目标路径
 *  @param isDeleteSrc 是否删除源文件
 *
 *  @return 是否成功
 */
- (BOOL)copyAssetAtPath:(NSString *)srcPath toPath:(NSString *)dstPath isDeleteSrc:(BOOL)isDeleteSrc {
    if (srcPath.length == 0 || dstPath.length == 0)
        return NO;
    
    NSFileManager *fileManager = [NSFileManager defaultManager];
    BOOL isDirectory;
    BOOL isExists = [fileManager fileExistsAtPath:srcPath isDirectory:&isDirectory];
    if (isExists) {
        // 复制文件夹
        if (isDirectory) {
            // 获取文件夹下所有子文件名
            NSArray *fileNames = [fileManager contentsOfDirectoryAtPath:srcPath error:nil];
            for (NSString *fileName in fileNames) {
                BOOL isSuccess = [self copyAssetAtPath:[srcPath stringByAppendingPathComponent:fileName] toPath:[dstPath stringByAppendingPathComponent:fileName] isDeleteSrc:isDeleteSrc];
                if (!isSuccess)
                    return NO;
            }
            // 是否需要删除目录
            if (isDeleteSrc)
                [fileManager removeItemAtPath:srcPath error:nil];
            // 复制子文件全部成功
            return YES;
        }
        // 复制文件
        else {
            // 如果目标路径已存在, 先删除
            if ([fileManager fileExistsAtPath:dstPath])
                [fileManager removeItemAtPath:dstPath error:nil];

            // 目标路径的目录
            NSString *dstDir = [dstPath stringByDeletingLastPathComponent];
            BOOL isSubDirectory;
            BOOL isSubExists = [fileManager fileExistsAtPath:dstDir isDirectory:&isSubDirectory];
            // 如果目录不存在, 先创建
            if (!isSubExists)
                [fileManager createDirectoryAtPath:dstDir withIntermediateDirectories:YES attributes:nil error:nil];
            // 如果目录被文件占用, 先删除再创建
            if (isSubExists && !isSubDirectory) {
                [fileManager removeItemAtPath:dstDir error:nil];
                [fileManager createDirectoryAtPath:dstDir withIntermediateDirectories:YES attributes:nil error:nil];
            }
            
            // 删除源文件使用move, 保留源文件使用copy
            if (isDeleteSrc)
                return [fileManager moveItemAtPath:srcPath toPath:dstPath error:nil];
            else
                return [fileManager copyItemAtPath:srcPath toPath:dstPath error:nil];
        }
    }
    
    return NO;
}

/**
 *  保存资源版本
 *
 *  @param zip     资源包名
 *  @param version 资源版本
 *
 *  @return 是否成功
 */
- (BOOL)saveAssetVersionForZip:(NSString *)zip version:(NSString *)version {
    if (_storagePath.length == 0)
        return NO;
    
    // 资源版本保存目录
    NSString *assetVersionDirPath = [NSString stringWithFormat:@"%@/%@", _storagePath, ASSET_VERSION_NAME];

    NSFileManager *fileManager = [NSFileManager defaultManager];
    BOOL isDirectory;
    BOOL isExists = [fileManager fileExistsAtPath:assetVersionDirPath isDirectory:&isDirectory];
    // 目录类型异常清除后重新创建
    if (isExists && !isDirectory) {
        [fileManager removeItemAtPath:assetVersionDirPath error:nil];
        [fileManager createDirectoryAtPath:assetVersionDirPath withIntermediateDirectories:YES attributes:nil error:nil];
    }
    // 创建目录
    if (!isExists)
        [fileManager createDirectoryAtPath:assetVersionDirPath withIntermediateDirectories:YES attributes:nil error:nil];
    
    // 资源版本存放路径 .../.ver/asset.zip.ver
    NSString *assetVersionFilePath = [NSString stringWithFormat:@"%@/%@%@", assetVersionDirPath, zip, ASSET_VERSION_NAME];
    
    return [[version dataUsingEncoding:NSUTF8StringEncoding] writeToFile:assetVersionFilePath atomically:YES];
}

/**
 *  读取资源版本
 *
 *  @param zip 资源包名
 *
 *  @return 资源版本
 */
- (NSString *)readAssetVersionForZip:(NSString *)zip {
    if (_storagePath.length == 0)
        return nil;

    // 资源版本存放路径 .../.ver/asset.zip.ver
    NSString *assetVersionFilePath = [NSString stringWithFormat:@"%@/%@/%@%@", _storagePath, ASSET_VERSION_NAME, zip, ASSET_VERSION_NAME];
    return [NSString stringWithContentsOfFile:assetVersionFilePath encoding:NSUTF8StringEncoding error:nil];
}

/**
 *  初始化资源信息列表
 *
 *  @param path 主配置文件路径
 *
 *  @return 资源列表 或 空
 */
- (NSArray *)assetInfoListWhitPath:(NSString *)path {
    // 判断主配置文件是否存在
    BOOL isDirectory;
    BOOL isExists = [[NSFileManager defaultManager] fileExistsAtPath:path isDirectory:&isDirectory];
    
    if (isExists && !isDirectory) {
        NSData *data = [NSData dataWithContentsOfFile:path];
        if (data) {
            NSDictionary *json = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
            if (json) {
                NSMutableArray *assetInfoList = [NSMutableArray array];
                NSArray *jsonAssetInfoList = [json objectForKey:ASSET_ROOT_NODE_NAME];
                for (int i = 0; i < jsonAssetInfoList.count; i++) {
                    AHAssetInfoModel *assetInfo = [[AHAssetInfoModel alloc] initWithJson:jsonAssetInfoList[i]];
                    [assetInfoList addObject:assetInfo];
                }
                return assetInfoList;
            }
        }
    }
    
    return nil;
}

/**
 *  字符串生成key
 *
 *  @param str 字符串
 *
 *  @return key 或 空
 */
- (NSString *)keyWithString:(NSString *)str {
    if (str.length > 0) {
        NSInteger halfLength = str.length / 2;
        return [NSString stringWithFormat:@"%d%d", [self hashCode:[str substringToIndex:halfLength]], [self hashCode:[str substringFromIndex:halfLength]]];
    }
    return nil;
}

- (int)hashCode:(NSString *)str {
    int h = 0;
    for (int i = 0; i < str.length; i++) {
        h = 31 * h + [str characterAtIndex:i];
    }
    return h;
}

@end


@implementation AHAssetInfoModel

- (instancetype)initWithJson:(NSDictionary *)json {
    self = [super init];
    if (self) {
        if (json.count > 0) {
            self.name = [json objectForKey:@"name"];
            self.ver = [json objectForKey:@"ver"];
            self.zip = [json objectForKey:@"zip"];
        }
    }
    return self;
}

@end
