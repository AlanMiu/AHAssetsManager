//
//  AHNetworkManager.m
//  AHKit
//
//  Created by Alan Miu on 15/9/17.
//  Copyright (c) 2015年 AutoHome. All rights reserved.
//

#import "AHNetworkManager.h"
#import "AFHTTPRequestOperationManager.h"
#import "AHHttpRequest.h"

#define MAX_THREAD_COUNT 4

@interface AHNetworkManager () {
    NSOperationQueue *_queue;
}

@end

@implementation AHNetworkManager


+ (instancetype)sharedManager {
    static AHNetworkManager *networkManager = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        networkManager = [[self alloc] init];
    });
    return networkManager;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _queue = [[NSOperationQueue alloc] init];
        _queue.maxConcurrentOperationCount = MAX_THREAD_COUNT;
    }
    return self;
}

- (void)addHttpRequest:(AHHttpRequest *)request {
    if (request) {
        [_queue addOperation:[request requestOperation]];
    }
}

- (void)setMaxThreadCount:(NSUInteger)count {
    _maxThreadCount = count;
    _queue.maxConcurrentOperationCount = count;
}


@end



