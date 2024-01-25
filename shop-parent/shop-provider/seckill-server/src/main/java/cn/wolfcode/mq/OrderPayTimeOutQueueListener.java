package cn.wolfcode.mq;

import cn.wolfcode.service.IOrderInfoService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(consumerGroup = "OrderPayTimeOutGroup",topic = MQConstant.ORDER_PAY_TIMEOUT_TOPIC)
public class OrderPayTimeOutQueueListener implements RocketMQListener<OrderMQResult> {

    @Autowired
    private IOrderInfoService orderInfoService;

    @Override
    public void onMessage(OrderMQResult orderMQResult) {
        // 进入这，说明已经过10分钟了
        // 取消订单
        System.out.println("超时取消订单逻辑");
        orderInfoService.cancelOrder(orderMQResult.getOrderNo());
    }
}
