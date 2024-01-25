package cn.wolfcode.web.controller;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.web.CommonCodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.DateUtil;
import cn.wolfcode.util.UserUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpRequest;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by shiyi
 */
@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;

    @RequestMapping("/doSeckill")
    @RequireLogin
    public Result<String>doSeckill(Integer time, Long seckillId, HttpServletRequest request){
        // 1.判断是否处于抢购的时间
        SeckillProductVo seckillProductVo = seckillProductService.find(time, seckillId);
        boolean legalTime = DateUtil.isLegalTime(seckillProductVo.getStartDate(), seckillProductVo.getTime());
//        if(!legalTime){
//            return Result.error(CommonCodeMsg.ILLEGAL_OPERATION);
//        }
        // 2.一个用户只能抢购一个商品
        // 获取token信息
        String token = request.getHeader(CommonConstants.TOKEN_NAME);
        // 根据token从redis中获取手机号
        String userPhone = UserUtil.getUserPhone(redisTemplate, token);
        OrderInfo orderInfo = orderInfoService.findByPhoneAndSeckillId(userPhone,seckillId);
        if(orderInfo!=null){
            // 提示重复下单
            System.out.println("11");
            return Result.error(SeckillCodeMsg.REPEAT_SECKILL);
        }

        // 优化:使用redis控制秒杀请求的人数
        String seckillStockCountKey = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(String.valueOf(time));
        Long remainCount = redisTemplate.opsForHash().increment(seckillStockCountKey, String.valueOf(seckillId), -1);
        if(remainCount<0){
            return Result.error(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }

        // 3.保证库存数量足够
        if(seckillProductVo.getStockCount()<=0){
            return Result.error(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        orderInfo = orderInfoService.doSeckill(userPhone,seckillProductVo);

        return Result.success();
    }

    @RequestMapping("/find")
    @RequireLogin
    public Result<OrderInfo>find(String orderNo,HttpServletRequest request){
        OrderInfo orderInfo=orderInfoService.findByOrderNo(orderNo);
        // 只能看自己的订单
        String token = request.getHeader(CommonConstants.TOKEN_NAME);
        String userPhone = UserUtil.getUserPhone(redisTemplate, token);
        if(!userPhone.equals(String.valueOf(orderInfo.getUserId()))){
            return Result.error(CommonCodeMsg.ILLEGAL_OPERATION);
        }
        return Result.success(orderInfo);
    }
}
