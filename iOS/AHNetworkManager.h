//
//  AHNetworkManager.h
//  AHKit
//
//  Created by Alan Miu on 15/9/17.
//  Copyright (c) 2015年 AutoHome. All rights reserved.
//

#import <Foundation/Foundation.h>

@class AHHttpRequest;

@interface AHNetworkManager : NSObject

@property (nonatomic) NSUInteger maxThreadCount;

+ (instancetype)sharedManager;

- (void)addHttpRequest:(AHHttpRequest *)request;

@end

