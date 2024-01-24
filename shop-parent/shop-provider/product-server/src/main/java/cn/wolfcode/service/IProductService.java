package cn.wolfcode.service;

import cn.wolfcode.domain.Product;

import java.util.List;

/**
 * Created by shiyi
 */
public interface IProductService {
    List<Product> queryByIds(List<Long> productIds);
}
