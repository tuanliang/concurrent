package cn.wolfcode.service;

import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;

import java.util.List;

/**
 * Created by shiyi
 */
public interface ISeckillProductService {
    /**
     * 查询秒杀列表的数据
     * @param time
     * @return
     */
    List<SeckillProductVo> queryByTime(Integer time);
}
