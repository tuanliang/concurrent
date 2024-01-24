package cn.wolfcode.web.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.service.IProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
