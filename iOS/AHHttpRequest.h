//
//  AHHttpRequest.h
//  AHKit
//
//  Created by Alan Miu on 15/11/6.
//  Copyright (c) 2015年 AutoHome. All rights reserved.
//

#import <Foundation/Foundation.h>

@class AHHttpRequestFileData;

typedef NS_ENUM(NSInteger, AHHttpMethod) {
    AHHttpMethodGet = 0,
    AHHttpMethodPost,
};

typedef NS_ENUM(NSInteger, AHHttpResponseSerializer) {
    AHHttpResponseSerializerData = 0,
    AHHttpResponseSerializerJson,
    AHHttpResponseSerializerXml,
    AHHttpResponseSerializerPlist,
};

@interface AHHttpRequest : NSObject

typedef void (^AHHttpRequestUploadBlock)(AHHttpRequest *request, NSInteger length, NSInteger progress, NSInteger total);
typedef void (^AHHttpRequestDownloadBlock)(AHHttpRequest *request, NSUInteger length, NSUInteger progress, NSUInteger total);
typedef void (^AHHttpRequestSuccessBlock)(AHHttpRequest *request, id response);
typedef void (^AHHttpRequestFailureBlock)(AHHttpRequest *request, NSError *error);

@property (nonatomic, strong) NSString *url;            // 链接
@property (nonatomic, strong) NSDictionary *headers;    // 请求头
@property (nonatomic, strong) NSDictionary *params;     // 请求参数
@property (nonatomic) AHHttpMethod method;              // 请求方法

@property (nonatomic) AHHttpResponseSerializer responseSerializer;  // 响应数据序列化
@property (nonatomic, strong) NSSet *acceptableContentTypes;        // 可接受的内容类型

@property (nonatomic, strong) NSInputStream *inputStream;           // 输入数据
@property (nonatomic, strong) NSOutputStream *outputStream;         // 输出数据

@property (nonatomic, strong) AHHttpRequestFileData *fileData;      // 有问题, 优化中...

@property (nonatomic) NSTimeInterval timeout;                       // 请求超时时间
@property (nonatomic) NSOperationQueuePriority queuePriority;       // 请求优先级
@property (nonatomic, readonly) NSTimeInterval requestTime;         // 请求耗时


// statusCode

/**
 *  初始化网络请求
 *
 *  @param url     链接
 *  @param params  请求参数
 *  @param method  请求方法
 *  @param success 成功回调
 *  @param failure 失败回调
 *
 *  @return 网络请求
 */
+ (instancetype)requestWhitUrl:(NSString *)url
                        params:(NSDictionary *)params
                        method:(AHHttpMethod)method
                       success:(AHHttpRequestSuccessBlock)success
                       failure:(AHHttpRequestFailureBlock)failure;

/**
 *  初始化网络请求
 *
 *  @param url     链接
 *  @param headers 请求头
 *  @param params  请求参数
 *  @param method  请求方法
 *  @param success 成功回调
 *  @param failure 失败回调
 *
 *  @return 网络请求
 */
- (instancetype)initWhitUrl:(NSString *)url
                    headers:(NSDictionary *)headers
                     params:(NSDictionary *)params
                     method:(AHHttpMethod)method
                    success:(AHHttpRequestSuccessBlock)success
                    failure:(AHHttpRequestFailureBlock)failure;

/**
 *  设置上传进度回调
 *
 *  @param upload 上传回调
 */
- (void)setUploadProgressBlock:(AHHttpRequestUploadBlock)upload;

/**
 *  设置下载进度回调
 *
 *  @param download 下载回调
 */
- (void)setDownloadProgressBlock:(AHHttpRequestDownloadBlock)download;

/**
 *  设置请求成功和失败回调
 *
 *  @param success 成功回调
 *  @param failure 失败回调
 */
- (void)setCompletionBlockWithSuccess:(AHHttpRequestSuccessBlock)success failure:(AHHttpRequestFailureBlock)failure;

/**
 *  获取请求操作(for AHNetworkManager)
 *
 *  @return 请求操作
 */
- (NSOperation *)requestOperation;

/**
 *  开始请求
 */
- (void)start;

/**
 *  取消请求
 */
- (void)cancel;

/**
 *  请求响应数据
 *
 *  @return 响应数据
 */
- (NSData *)responseData;

@end

@interface AHHttpRequestFileData : NSObject

@property (nonatomic, strong) NSData *data;
@property (nonatomic, strong) NSString *name;
@property (nonatomic, strong) NSString *fileName;
@property (nonatomic, strong) NSString *mimeType;

- (instancetype)initWhitData:(NSData *)data name:(NSString *)name fileName:(NSString *)fileName mimeType:(NSString *)mimeType;

@end