# 项目启动

```
/usr/local/nacos/bin/startup.sh -m standalone
nohup sh mqnamesrv &
nohup sh mqbroker -n localhost:9876 -c /usr/local/rocketmq-4.4/conf/broker.conf &
/usr/local/zookeeper-3.4.11/bin/zkServer.sh start
```



# 限时抢购项目导入文档

1.使用git clone将项目导入到你的本地目录中

2.通过idea工具将`frontend-server`项目和`shop-parent`导入

3.在flash_sale/shop-parent/配置文件/SQL脚本下的文件导入到数据库中.

```sql
-shop-intergral.sql
-shop-product.sql
-shop-seckill.sql
-shop-uaa.sql
```

导入后的结果如下:

**![image-20201203115901889](图片/image-20201203115903310.png)**

4.本项目需要依赖RocketMQ,Redis,Nacos,Zookeeper,在项目运行之前确保这几个软件在你的电脑/虚拟机/远程服务器中已经安装好.比如在服务器上已经安装好之后，使用命令`jps`可以看到这几个进程:

**![image-20201203120434228](图片/image-20201203120437631.png)**

5.在`flash_sale/shop-parent/配置文件/nacos配置`中找到项目的配置文件压缩包`nacos_config.zip`，访问nacos管控台,导入配置信息.

![image-20201203142918858](图片/image-20201203142918858.png)

6.修改配置信息

- `rocketmq-config-dev.yaml`:修改成你的RocketMQ的地址
- `redis-config-dev.yaml`:修改成你的Redis的地址
- `job-service-dev.yaml`:修改成你的Zookeeper的地址
- `nacos-discovery-config-dev.yaml`:修改成你的Nacos的地址

项目中的`bootstrap.yml`的地址都需要修改

**![image-20201203143536265](图片/image-20201203143536265.png)**


# 阅读代码 

## 1.前后端分离，跨域问题如何解决？

在网关的`CorsConfig`中

## 2.在微服务中如何获取到真实的IP

![登录2](.\图片\ip.png)

## 3.登录流程图

![登录2](.\图片\登录1.png)



![登录2](.\图片\登录2.png)



## 4.用Redis+Token实现分布式Session，如何跟新redis时间？

在代码CommonFilter中



# 实战

## 秒杀列表需求

![秒杀列表需求](.\图片\秒杀列表需求.png)

![秒杀列表需求](.\图片\秒杀列表需求2.png)

![秒杀列表需求](.\图片\秒杀列表需求1.png)

![秒杀列表需求](.\图片\秒杀列表需求3.png)



## 秒杀初步实现

**feign远程调用返回值**

![秒杀列表需求](.\图片\feign远程调用返回值.png)



**远程调用获取的数据**

当远程返回为null或者状态码不是200的时候，获取数据失败

![image-20240123201106980](.\图片\返回值.png)

![image-20240123201106980](.\图片\返回值2.png)

1.秒杀服务

```java
@RequestMapping("/queryByTime")
    public Result<List<SeckillProductVo>>queryByTime(Integer time){
        return Result.success(seckillProductService.queryByTime(time));
    }
```

```java
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
```



加上服务降级的配置

```yaml
feign:
  sentinel:
    enabled: true
```

```java
@FeignClient(name = "product-service",fallback = ProductFeignFallback.class)
public interface ProductFeignApi {
    @RequestMapping("/product/queryByIds")
    Result<List<Product>>queryByIds(@RequestParam List<Long>productIds);
}
```

```java
@Component
public class ProductFeignFallback implements ProductFeignApi {

    @Override
    public Result<List<Product>> queryByIds(List<Long> productIds) {
        // 返回兜底数据
        return null;
    }
}
```

2.商品服务

```java
@RestController
@RequestMapping("/product")
public class ProductFeignClient {

    @Autowired
    private IProductService productService;

    @RequestMapping("/queryByIds")
    public Result<List<Product>> queryByIds(@RequestParam List<Long>productIds){
        return Result.success(productService.queryByIds(productIds));
    }
}
```

```java
@Service
public class ProductServiceImpl implements IProductService {
    @Autowired
    private ProductMapper productMapper;

    @Override
    public List<Product> queryByIds(List<Long> productIds) {
        if(productIds==null||productIds.size()==0){
            return Collections.EMPTY_LIST;
        }
        return productMapper.queryProductByIds(productIds);
    }
}
```



## 秒杀需求分析

需求:
1.购买数量的限制,一个人只能抢购一次.

⒉购买的时候需要登录才能进行抢购. (在一定程度可以进行限流)

3.需要在给定的时间才能进行抢购

4.如果充足才能进行抢购

```
---原子性
创建秒杀订单
库存数量-1
---
```



### 登录才可以抢购分析与实现

![登录注解](.\图片\登录注解.png)

![登录注解2](.\图片\登录注解2.png)

`![登录注解3](.\图片\登录注解3.png)



## 秒杀功能

```java
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
        // 3.保证库存数量足够
        if(seckillProductVo.getStockCount()<=0){
            return Result.error(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        orderInfo = orderInfoService.doSeckill(userPhone,seckillProductVo);

        return Result.success();
    }
```

```java
@Service
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private RefundLogMapper refundLogMapper;

    @Override
    public OrderInfo findByPhoneAndSeckillId(String userPhone, Long seckillId) {
        return orderInfoMapper.findByPhoneAndSeckillId(userPhone,seckillId);
    }

    @Override
    @Transactional
    public OrderInfo doSeckill(String userPhone, SeckillProductVo seckillProductVo) {
        // 4.扣减数据库库存
        seckillProductService.dercStockCount(seckillProductVo.getId());
        // 5.创建秒杀订单
        OrderInfo orderInfo = createOrderInfo(userPhone,seckillProductVo);
        return  orderInfo;
    }

    private OrderInfo createOrderInfo(String userPhone, SeckillProductVo seckillProductVo) {
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(seckillProductVo,orderInfo);
        orderInfo.setUserId(Long.parseLong(userPhone));
        orderInfo.setCreateDate(new Date());
        orderInfo.setDeliveryAddrId(1L);
        orderInfo.setSeckillDate(seckillProductVo.getStartDate());
        orderInfo.setSeckillTime(seckillProductVo.getTime());
        orderInfo.setOrderNo(String.valueOf(IdGenerateUtil.get().nextId()));
        orderInfo.setSeckillId(seckillProductVo.getId());
        orderInfoMapper.insert(orderInfo);
        return orderInfo;
    }
}
```



### jmuter对秒杀列表和秒杀详情进行测试

![登录注解3](.\图片\测试1.png)

![登录注解3](.\图片\测试2.png)

![登录注解3](.\图片\测试3.png)

![登录注解3](.\图片\测试4.png)

![登录注解3](.\图片\测试5.png)

![登录注解3](.\图片\测试6.png)

![登录注解3](.\图片\测试7.png)

![登录注解3](.\图片\测试8.png)

### 模拟多用户抢商品

![登录注解3](.\图片\测试9.png)

![登录注解3](.\图片\测试9.2.png)

![登录注解3](.\图片\测试10.png)

![登录注解3](.\图片\测试11.png)

![登录注解3](.\图片\测试12.png)

结果数据库中出现超卖现象。后面会解决



## 定时上架的需求分析

![登录注解3](.\图片\定时上架.png)

```java
@Component
@Getter
@Setter
public class InitSeckillProductJob implements SimpleJob {

    @Value("${jobCron.initSeckillProduct}")
    private String cron;

    @Autowired
    private SeckillProductFeignAPI seckillProductFeignAPI;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void execute(ShardingContext shardingContext) {
        // 1.远程调用秒杀服务获取秒杀列表集合
        String time = shardingContext.getShardingParameter();
        Result<List<SeckillProductVo>> result = seckillProductFeignAPI.queryByTimeForJob(Integer.parseInt(time));
        if(result==null|| result.hasError()){
            // 通过管理员
            return;
        }
        List<SeckillProductVo> seckillProductVos = result.getData();
        // 2，删除之前的数据
        String key = JobRedisKey.SECKILL_PRODUCT_HASH.getRealKey(time);
        redisTemplate.delete(key);
        // 3.存储集合数据到redis中
        for (SeckillProductVo vo : seckillProductVos) {
            redisTemplate.opsForHash().put(key,String.valueOf(vo.getId()), JSON.toJSONString(vo));
        }
    }
}
```

```java
@FeignClient(name = "seckill-service",fallback = SeckillProductFeignFallbck.class)
public interface SeckillProductFeignAPI {
    @RequestMapping("/seckillProduct/queryByTimeForJob")
    Result<List<SeckillProductVo>>queryByTimeForJob(@RequestParam("time") Integer time);
}
```

```java
@Bean(initMethod = "init")
    public SpringJobScheduler initSPJob(CoordinatorRegistryCenter registryCenter, InitSeckillProductJob seckillProductJob){
        LiteJobConfiguration jobConfiguration = ElasticJobUtil.createJobConfiguration(
                seckillProductJob.getClass(), seckillProductJob.getCron(),3,
                "0=10,1=12,2=14",false);
        SpringJobScheduler springJobScheduler = new SpringJobScheduler(seckillProductJob, registryCenter,jobConfiguration );
        return springJobScheduler;
    }
```



```java
@RestController
@RequestMapping("/seckillProduct")
public class SeckillFeignClient {

    @Autowired
    private ISeckillProductService seckillProductService;

    @RequestMapping("/seckillProduct/queryByTimeForJob")
    public Result<List<SeckillProductVo>> queryByTimeForJob(@RequestParam("time") Integer time){
        return Result.success(seckillProductService.queryByTime(time));
    }
}
```

## 优化前面的秒杀列表和详情接口

```java
@RequestMapping("/queryByTime")
    public Result<List<SeckillProductVo>>queryByTime(Integer time){
//        return Result.success(seckillProductService.queryByTime(time));
        return Result.success(seckillProductService.queryByTimeFromCache(time));
    }

    @RequestMapping("/find")
    public Result<SeckillProductVo>find(Integer time,Long seckillId){
//        return Result.success(seckillProductService.find(time,seckillId));
        return Result.success(seckillProductService.findFromCache(time,seckillId));
    }
```

```java
@Override
    public List<SeckillProductVo> queryByTimeFromCache(Integer time) {
        String key = SeckillRedisKey.SECKILL_PRODUCT_HASH.getRealKey(String.valueOf(time));
        List<Object> objStrList = redisTemplate.opsForHash().values(key);
        List<SeckillProductVo>seckillProductVoList = new ArrayList<>();
        for (Object objStr : objStrList) {
            seckillProductVoList.add(JSON.parseObject((String) objStr,SeckillProductVo.class));
        }
        return seckillProductVoList;
    }

    @Override
    public SeckillProductVo findFromCache(Integer time, Long seckillId) {
        String key = SeckillRedisKey.SECKILL_PRODUCT_HASH.getRealKey(String.valueOf(time));
        Object obj = redisTemplate.opsForHash().get(key, String.valueOf(seckillId));
        SeckillProductVo vo = JSON.parseObject((String)obj,SeckillProductVo.class);
        return vo;
    }
```

### 优化之后进行测试

![登录注解3](.\图片\优化测试.png)

![登录注解3](.\图片\优化测试2.png)

 ## 优化秒杀功能，解决超卖问题

![登录注解3](.\图片\锁机制.png)

超卖兜底解决方案：

![登录注解3](.\图片\超卖兜底解决.png)

![redis原子性递减控制秒杀请求](.\图片\redis原子性递减控制秒杀请求.png)

![redis原子性递减控制秒杀请求2](.\图片\redis原子性递减控制秒杀请求2.png)

在秒杀服务OrderInfoController中加入

```java
// 优化:使用redis控制秒杀请求的人数
        String seckillStockCountKey = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(String.valueOf(time));
        Long remainCount = redisTemplate.opsForHash().increment(seckillStockCountKey, String.valueOf(seckillId), -1);
        if(remainCount<0){
            return Result.error(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
```

在定时任务InitSeckillProductJob加入

```java
// 优化：库存数量key
String seckillStockCountKey = JobRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(time);
redisTemplate.delete(seckillStockCountKey);

// 优化：将库存同步到redis
redisTemplate.opsForHash().put(seckillStockCountKey,String.valueOf(vo.getId()),String.valueOf(vo.getStockCount()));
```

