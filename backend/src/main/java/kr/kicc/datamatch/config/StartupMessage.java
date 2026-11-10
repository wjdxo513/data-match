package kr.kicc.datamatch.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 기동이 끝나면 접속 주소를 콘솔에 출력한다(기술 설계서 3.2절, SETUP-03). */
@Component
public class StartupMessage {

    private final Environment environment;

    public StartupMessage(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printAccessUrl() {
        System.out.println(accessMessage());
    }

    String accessMessage() {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "18080"));
        return "[DataMatch] 시작되었습니다. 브라우저에서 http://localhost:" + port + " 로 접속하세요.";
    }
}
