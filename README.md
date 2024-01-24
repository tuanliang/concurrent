# 项目启动

```
/usr/local/nacos/bin/startup.sh -m standalone
nohup sh mqnamesrv &
nohup sh mqbroker -n localhost:9876 -c /usr/local/rocketmq-4.4/conf/broker.conf &
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





