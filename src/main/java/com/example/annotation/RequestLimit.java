package com.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestLimit {
    // 限制时间 单位：秒(默认值：一分钟）
    long period() default 60;

    // 允许请求的次数(默认值：5次）
    long count() default 5;
}
