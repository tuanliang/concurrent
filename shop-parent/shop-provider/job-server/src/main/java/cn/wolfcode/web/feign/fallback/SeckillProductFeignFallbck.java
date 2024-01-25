package cn.wolfcode.web.feign.fallback;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.web.feign.SeckillProductFeignAPI;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SeckillProductFeignFallbck implements SeckillProductFeignAPI {
    @Override
    public Result<List<SeckillProductVo>> queryByTimeForJob(Integer time) {
        return null;
    }
}
