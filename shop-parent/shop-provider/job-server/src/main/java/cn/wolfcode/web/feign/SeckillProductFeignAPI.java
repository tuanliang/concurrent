package cn.wolfcode.web.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.web.feign.fallback.SeckillProductFeignFallbck;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "seckill-service",fallback = SeckillProductFeignFallbck.class)
public interface SeckillProductFeignAPI {
    @RequestMapping("/seckillProduct/queryByTimeForJob")
    Result<List<SeckillProductVo>>queryByTimeForJob(@RequestParam("time") Integer time);
}
