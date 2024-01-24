package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mapper.SeckillProductMapper;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.web.feign.ProductFeignApi;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Created by shiyi
 */
@Service
public class SeckillProductServiceImpl implements ISeckillProductService {
    @Autowired
    private SeckillProductMapper seckillProductMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private ProductFeignApi productFeignApi;

    @Override
    public List<SeckillProductVo> queryByTime(Integer time) {
        // 1.查询秒杀商品数据(根据场次)
        List<SeckillProduct> seckillProducts = seckillProductMapper.queryCurrentlySeckillProduct(time);
        if(seckillProducts.size()==0){
            return Collections.EMPTY_LIST;
        }
        // 2.便利秒杀商品集合数据，获取商品id集合
        List<Long>productIds = new ArrayList<>();
        for (SeckillProduct seckillProduct : seckillProducts) {
            productIds.add(seckillProduct.getProductId());
        }
        // 3.远程调用，获取商品集合
        Result<List<Product>> result = productFeignApi.queryByIds(productIds);
        if(result==null||result.hasError()){
            throw new BusinessException(SeckillCodeMsg.PRODUCT_SERVER_ERROR);
        }
        List<Product>productList = result.getData();
        Map<Long,Product>productMap = new HashMap<>();
        for (Product product : productList) {
            productMap.put(product.getId(),product);
        }
        // 4.将商品和秒杀商品数据集合，封装vo对象并返回
        List<SeckillProductVo>seckillProductVoList = new ArrayList<>();
        for (SeckillProduct seckillProduct : seckillProducts) {
            SeckillProductVo vo = new SeckillProductVo();
            Product product = productMap.get(seckillProduct.getProductId());
            BeanUtils.copyProperties(product,vo);
            BeanUtils.copyProperties(seckillProduct,vo);
            vo.setCurrentCount(seckillProduct.getStockCount());// 当前数量默认是商品数量
            seckillProductVoList.add(vo);
        }
        return seckillProductVoList;
    }
}
