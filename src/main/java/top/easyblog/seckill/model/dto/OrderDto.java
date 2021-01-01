package top.easyblog.seckill.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 * @author Huang Xin
 * @Description 订单消息传输对象
 * @data 2020/12/25 16:45
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class OrderDto implements Serializable {

    private String orderId;
    private Integer userId;
    private Integer itemId;
    private Integer promoId;
    private Integer amount;
    private String stockLogId;


    public static OrderDto createOrderDto(String orderId,Integer userId, Integer itemId, Integer promoId, Integer amount) {
        return new OrderDto(orderId,userId, itemId, promoId, amount,null);
    }

}
