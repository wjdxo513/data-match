package kr.kicc.datamatch.config;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;

/**
 * INT-01: 실행된 SQL을 기록하는 {@link DataSourceRegistry}를 만든다(테스트 전용).
 * 연결의 {@code prepareStatement}·{@code prepareCall}·{@code createStatement} 호출을 "풀 이름|SQL" 형식으로 기록한다.
 */
public final class RecordingRegistries {

    private RecordingRegistries() {
    }

    public static DataSourceRegistry create(DataMatchProperties properties, List<String> sink) {
        return new DataSourceRegistry(properties, config -> {
            HikariDataSource real = new HikariDataSource(config);
            String pool = config.getPoolName();
            return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                    new Class<?>[] {DataSource.class, AutoCloseable.class}, (proxy, method, args) -> {
                        try {
                            Object result = method.invoke(real, args);
                            if ("getConnection".equals(method.getName())) {
                                return recording((Connection) result, pool, sink);
                            }
                            return result;
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        });
    }

    private static Connection recording(Connection real, String pool, List<String> sink) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("prepareStatement") || name.equals("prepareCall")) {
                        sink.add(pool + "|" + args[0]);
                    } else if (name.equals("createStatement")) {
                        sink.add(pool + "|<createStatement>");
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
