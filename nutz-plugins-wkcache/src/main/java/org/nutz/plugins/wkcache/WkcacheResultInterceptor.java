package org.nutz.plugins.wkcache;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.nutz.aop.InterceptorChain;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.lang.Lang;
import org.nutz.lang.Strings;
import org.nutz.plugins.wkcache.annotation.CacheDefaults;
import org.nutz.plugins.wkcache.annotation.CacheResult;

import redis.clients.jedis.Jedis;

/**
 * Created by wizzer on 2017/6/14.
 */
@IocBean
public class WkcacheResultInterceptor extends AbstractWkcacheInterceptor {

    public void filter(InterceptorChain chain) throws Throwable {
        Method method = chain.getCallingMethod();
        CacheResult cacheResult = method.getAnnotation(CacheResult.class);
        String cacheKey = Strings.sNull(cacheResult.cacheKey());
        String cacheName = Strings.sNull(cacheResult.cacheName());
        int liveTime = 0;
        if (Strings.isBlank(cacheKey)) {
            cacheKey = method.getDeclaringClass().getName()
                    + "."
                    + method.getName()
                    + "#"
                    + Arrays.toString(chain.getArgs());
        }
        if (Strings.isBlank(cacheName)) {
            CacheDefaults cacheDefaults = method.getDeclaringClass()
                    .getAnnotation(CacheDefaults.class);
            cacheName = cacheDefaults != null ? cacheDefaults.cacheName() : "wk";
            liveTime = cacheDefaults != null ? cacheDefaults.cacheLiveTime() : 0;
        }
        Object obj;
        try (Jedis jedis = jedisAgent().getResource()) {
            byte[] bytes = jedis.get((cacheName + ":" + cacheKey).getBytes());
            if (bytes == null) {
                chain.doChain();
                obj = chain.getReturn();
                jedis.set((cacheName + ":" + cacheKey).getBytes(), Lang.toBytes(obj));
                if (liveTime > 0) {
                    jedis.expire((cacheName + ":" + cacheKey).getBytes(), liveTime);
                }
            } else {
                obj = Lang.fromBytes(bytes, method.getReturnType());
            }
        }
        chain.setReturnValue(obj);
    }
}
