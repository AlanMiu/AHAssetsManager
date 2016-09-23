//
//  AHAssetsManager.h
//  AHKit
//
//  Created by Alan Miu on 15/9/17.
//  Copyright (c) 2015年 AutoHome. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface AHAssetsManager : NSObject

@property (nonatomic, readonly) NSString *assetDirUrl;  // 资源目录链接
@property (nonatomic, readonly) NSString *storagePath;  // 存储资源路径
@property (nonatomic, readonly) NSString *bundlePath;   // 内置资源路径

@property (nonatomic, readonly) NSArray *assetInfoList; // 资源信息列表

/**
 *  单例资源管理器
 *
 *  @param assetDirUrl  资源目录链接
 *  @param storagePath  存储资源路径
 *  @param bundlePath   内置资源路径
 *
 *  @return 资源管理器
 */
+ (instancetype)sharedManager:(NSString *)assetDirUrl storagePath:(NSString *)storagePath bundlePath:(NSString *)bundlePath;

/**
 *  快捷获取资源管理器(必须先调用+sharedManager:带参数方法, 否则返回空)
 *
 *  @return 资源管理器
 */
+ (instancetype)sharedManager;

/**
 *  初始化资源管理器
 *
 *  @param assetDirUrl  资源目录链接
 *  @param storagePath  存储资源路径
 *  @param bundlePath   内置资源路径
 *
 *  @return 资源管理器
 */
- (instancetype)initWithUrl:(NSString *)assetDirUrl storagePath:(NSString *)storagePath bundlePath:(NSString *)bundlePath;

/**
 *  获取配置信息
 *
 *  @param name 文件名
 *
 *  @return 配置信息 或 空
 */
- (NSString *)configForName:(NSString *)name;

/**
 *  获取资源路径
 *
 *  @param name 文件名
 *
 *  @return 资源路径 或 空
 */
- (NSString *)assetPathForName:(NSString *)name;

@end


/**
 *  资源信息
 */
@interface AHAssetInfoModel : NSObject

@property (nonatomic, strong) NSString *name;   // 资源名称
@property (nonatomic, strong) NSString *ver;    // 资源版本
@property (nonatomic, strong) NSString *zip;    // 资源包名

- (instancetype)initWithJson:(NSDictionary *)json;

@end

