package cn.wolfcode.mq;

import cn.wolfcode.service.ISeckillProductService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(consumerGroup = "OrderResultFailGroup",
        topic = MQConstant.ORDER_RESULT_TOPIC,selectorExpression = MQConstant.ORDER_RESULT_FAIL_TAG)
public class OrderResutlFailQueueListenter implements RocketMQListener<OrderMQResult> {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Override
    public void onMessage(OrderMQResult orderMQResult) {
        System.out.println("失败进行预库存回补");
        seckillProductService.syncStockToRedis(orderMQResult.getTime(),orderMQResult.getSeckillId());
    }
}
