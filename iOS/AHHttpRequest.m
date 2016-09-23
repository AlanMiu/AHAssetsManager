//
//  AHHttpRequest.m
//  AHKit
//
//  Created by Alan Miu on 15/11/6.
//  Copyright (c) 2015年 AutoHome. All rights reserved.
//

#import "AHHttpRequest.h"
#import "AHNetworkManager.h"

#import "AFNetworking.h"

#define HTTP_CONNECTION_TIMEOUT 30 * 1000

NSString * StringFromHttpMethod(AHHttpMethod method) {
    switch (method) {
        case AHHttpMethodGet:
            return @"GET";
        case AHHttpMethodPost:
            return @"POST";
    }
}

AFHTTPResponseSerializer * HttpResponseSerializer(AHHttpResponseSerializer serializer) {
    switch (serializer) {
        case AHHttpResponseSerializerData:
            return [AFHTTPResponseSerializer serializer];
        case AHHttpResponseSerializerJson:
            return [AFJSONResponseSerializer serializer];
        case AHHttpResponseSerializerXml:
            return [AFXMLParserResponseSerializer serializer];
        case AHHttpResponseSerializerPlist:
            return [AFPropertyListResponseSerializer serializer];
    }
}

@interface AHHttpRequest () {
    // 请求开始时间戳
    NSDate *_startTime;
    // 请求操作
    AFHTTPRequestOperation *_requestOperation;
}

@property (nonatomic, copy) AHHttpRequestUploadBlock upload;
@property (nonatomic, copy) AHHttpRequestDownloadBlock download;
@property (nonatomic, copy) AHHttpRequestSuccessBlock success;
@property (nonatomic, copy) AHHttpRequestFailureBlock failure;

@end

@implementation AHHttpRequest

#pragma mark Public Methods

+ (instancetype)requestWhitUrl:(NSString *)url
                       params:(NSDictionary *)params
                       method:(AHHttpMethod)method
                      success:(AHHttpRequestSuccessBlock)success
                      failure:(AHHttpRequestFailureBlock)failure {
    return [[self alloc] initWhitUrl:url headers:nil params:params method:method success:success failure:failure];
}

- (instancetype)initWhitUrl:(NSString *)url
                    headers:(NSDictionary *)headers
                     params:(NSDictionary *)params
                     method:(AHHttpMethod)method
                    success:(AHHttpRequestSuccessBlock)success
                    failure:(AHHttpRequestFailureBlock)failure {
    self = [super init];
    if (self) {
        _timeout = HTTP_CONNECTION_TIMEOUT;
        _url = url;
        _method = method;
        _params = params;
        _headers = headers;
        self.success = success;
        self.failure = failure;
    }
    return self;
}

- (void)setUploadProgressBlock:(AHHttpRequestUploadBlock)upload {
    self.upload = upload;
}

- (void)setDownloadProgressBlock:(AHHttpRequestDownloadBlock)download {
    self.download = download;
}

- (void)setCompletionBlockWithSuccess:(AHHttpRequestSuccessBlock)success failure:(AHHttpRequestFailureBlock)failure {
    self.success = success;
    self.failure = failure;
}

- (NSOperation *)requestOperation {
    // 记录请求开始时间
    _startTime = [NSDate date];
    _requestOperation = [self createHttpRequestOperation];
    return _requestOperation;
}

- (void)start {
    // 如果请求已存在, 先取消
    if (_requestOperation)
        [self cancel];
    [[AHNetworkManager sharedManager] addHttpRequest:self];
}

- (void)cancel {
    if (_requestOperation) {
        [_requestOperation cancel];
        _requestOperation = nil;
    }
}

- (NSData *)responseData {
    return _requestOperation.responseData;
}

#pragma mark AFNetworking Methods

- (AFHTTPRequestOperation *)createHttpRequestOperation {
    // 请求序列化
    NSError *serializationError = nil;
    NSMutableURLRequest *request = nil;
    
    AFHTTPRequestSerializer *requestSerializer = [AFHTTPRequestSerializer serializer];
    if (_fileData) {
        request = [requestSerializer multipartFormRequestWithMethod:StringFromHttpMethod(_method) URLString:_url parameters:_params constructingBodyWithBlock:^(id<AFMultipartFormData> formData) {
            [formData appendPartWithFileData:_fileData.data name:_fileData.name fileName:_fileData.fileName mimeType:_fileData.mimeType];
        } error:&serializationError];
    } else {
        request = [requestSerializer requestWithMethod:StringFromHttpMethod(_method) URLString:_url parameters:_params error:&serializationError];
    }
    
    if (serializationError) {
        if (_failure) {
            dispatch_async(dispatch_get_main_queue(), ^{
                _failure(self, serializationError);
            });
        }
        return nil;
    }
    
    // 设置超时时间
    request.timeoutInterval = _timeout;
    
    // 设置请求头
    for (NSString *key in _headers) {
        [request setValue:_headers[key] forHTTPHeaderField:key];
    }

    // 返回数据序列化
    AFHTTPResponseSerializer *responseSerializer = HttpResponseSerializer(_responseSerializer);
    if (_acceptableContentTypes)
        responseSerializer.acceptableContentTypes = _acceptableContentTypes;
    
    // 创建请求
    AFHTTPRequestOperation *operation = [[AFHTTPRequestOperation alloc] initWithRequest:request];
    operation.queuePriority = _queuePriority;
    operation.responseSerializer = responseSerializer;
//    operation.shouldUseCredentialStorage = _shouldUseCredentialStorage;
//    operation.credential = _credential;
//    operation.securityPolicy = [AFSecurityPolicy defaultPolicy];
    
    // 自定义输入输出流
    if (_inputStream)
        operation.inputStream = _inputStream;
    if (_outputStream)
        operation.outputStream = _outputStream;
    
    // 上传回调
    if (_upload) {
        [operation setUploadProgressBlock:^(NSUInteger bytesWritten, long long totalBytesWritten, long long totalBytesExpectedToWrite) {
            if (_upload)
                _upload(self, bytesWritten, totalBytesWritten, totalBytesExpectedToWrite);
        }];
    }
    // 下载回调
    if (_download) {
        [operation setDownloadProgressBlock:^(NSUInteger bytesRead, long long totalBytesRead, long long totalBytesExpectedToRead) {
            if (_download)
                _download(self, bytesRead, totalBytesRead, totalBytesExpectedToRead);
        }];
    }
    // 请求成功或失败
    [operation setCompletionBlockWithSuccess:^(AFHTTPRequestOperation *operation, id responseObject) {
        // 记录请求结束时间
        _requestTime = [[NSDate date] timeIntervalSinceDate:_startTime];
        if (_success)
            _success(self, responseObject);
        _requestOperation = nil;
    } failure:^(AFHTTPRequestOperation *operation, NSError * error) {
        // 记录请求结束时间
        _requestTime = [[NSDate date] timeIntervalSinceDate:_startTime];
        if (_failure)
            _failure(self, error);
        _requestOperation = nil;
    }];
    
    return operation;
}

@end


@implementation AHHttpRequestFileData

- (instancetype)initWhitData:(NSData *)data name:(NSString *)name fileName:(NSString *)fileName mimeType:(NSString *)mimeType {
    self = [super init];
    if (self) {
        _data = data;
        _name = name;
        _fileName = fileName;
        _mimeType = mimeType;
    }
    return self;
}

@end