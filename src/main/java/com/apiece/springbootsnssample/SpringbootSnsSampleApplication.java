package com.apiece.springbootsnssample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration;

// Redis 세션 스타터가 있어도 서블릿 컨테이너(Tomcat) 기본 인메모리 세션을 사용하도록 자동설정을 제외한다.
@SpringBootApplication(exclude = SessionDataRedisAutoConfiguration.class)
public class SpringbootSnsSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootSnsSampleApplication.class, args);
    }

}
