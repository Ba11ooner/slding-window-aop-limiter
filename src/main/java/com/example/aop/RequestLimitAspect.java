package com.example.aop;

import com.example.annotation.RequestLimit;
import com.example.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

import static com.example.common.ErrorCode.FORBIDDEN_ERROR;
import static com.example.common.ErrorCode.PARAMS_ERROR;

@Aspect
@Component
public class RequestLimitAspect {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    RedisTemplate<String, Long> redisTemplate;

    // 定义切点
    @Pointcut("@annotation(requestLimit)")
    public void controllerAspect(RequestLimit requestLimit) {
    }

    // 织入逻辑
    // 在指定切点周围添加业务逻辑
    @Around("controllerAspect(requestLimit)")
    public Object doAround(ProceedingJoinPoint joinPoint, RequestLimit requestLimit) throws Throwable {
        // 获取注解中记录的属性
        long period = requestLimit.period(); // 窗口大小
        long limitCount = requestLimit.count(); // 限制次数

        // 引入 ZSet
        ZSetOperations<String, Long> zSetOperations = redisTemplate.opsForZSet();

        // region 记录请求
        // 获取请求：根据参数类型获取 HttpServletRequest
        Object[] args = joinPoint.getArgs();
        HttpServletRequest httpServletRequest = null;
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest) {
                httpServletRequest = (HttpServletRequest) arg;
                break;
            }
        }

        // 从 HttpServletRequest 中获取 IP 和 URI
        // 例：访问 https://www.example.com/products?id=123
        // 假设 www.example.com 对应的 IP 地址为：192.168.1.1
        // getRemoteAddr() → 192.168.1.1
        // getRequestURI → /products?id=123
        String ip = "";
        String uri = "";
        if (httpServletRequest != null) {
            ip = httpServletRequest.getRemoteAddr();
            uri = httpServletRequest.getRequestURI();
            System.out.println(ip);
            System.out.println(uri);
        } else {
            // 没有找到HttpServletRequest参数
            throw new BusinessException(PARAMS_ERROR, "没有找到HttpServletRequest参数");
        }

        // 利用 URI 和 IP 拼接 Key
        String key = "req_limit_".concat(uri).concat(ip);

        // 获取当前时间戳，作为 Value 和 Score
        long currentMs = System.currentTimeMillis();

        // add 参数说明：
        // key：键
        // value：值
        // score ：排序权重
        zSetOperations.add(key, currentMs, currentMs);
        // 设置过期时间：安全机制，避免长间隔请求持续占用内存。
        // 即确保内存中的滑动窗口数据不会一直累积，避免内存占用过多。
        // 因为窗口控制仅在请求调用时进行，如果长期不调用接口，又不设置过期时间，会导致不必要的内存消耗。
        redisTemplate.expire(key, period, TimeUnit.SECONDS);
        //endregion

        // region 控制窗口
        // 删除滑动窗口以外的值，根据当前时间和注解中设置的 period 确定窗口大小
        // removeRangeByScore 参数说明：
        // key：表示有序集合的键名。
        // minScore：表示删除范围的最小分数。
        // maxScore：表示删除范围的最大分数。
        zSetOperations.removeRangeByScore(key, 0, currentMs - period * 1000);
        //endregion

        // region 判断当前访问次数是否已经大于限制次数
        // 统计当前访问次数
        // zCard 功能说明：获取有序集合中成员的数量。
        // zCard 参数说明：key，表示有序集合的键名。
        Long count = zSetOperations.zCard(key);
        if (count > limitCount) {
            logger.error("接口拦截：{} 请求超过限制频率【{}次/{}s】,IP为{}", uri, limitCount, period, ip);
            throw new BusinessException(FORBIDDEN_ERROR, "请求超过限制频率");
        }
        //endregion

        // 执行用户请求
        return joinPoint.proceed();
    }
}
