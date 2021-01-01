package top.easyblog.seckill.conf.redis.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.joda.time.DateTime;

import java.io.IOException;

/**
 * 定制对于joad时间的序列化方式
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/16 18:28
 */
public class JodaDataTimeSerializer extends JsonSerializer<DateTime> {


    @Override
    public void serialize(DateTime dateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
          jsonGenerator.writeString(dateTime.toString("yyyy-MM-dd HH-mm-ss"));
    }
}
