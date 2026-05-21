package com.liveklass.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("수강 신청 시스템 API")
                        .description("크리에이터가 강의를 개설하고 수강생이 신청·결제·취소하는 수강 신청 시스템입니다.")
                        .version("1.0.0"));
    }
}
