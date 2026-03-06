package com.server.util;

import com.server.entities.Message;
import com.server.entities.User;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import java.util.Properties;

public class HibernateUtil {
    private static SessionFactory sessionFactory;

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            try {
                Configuration configuration = new Configuration();

                // Hibernate settings equivalent to hibernate.cfg.xml's properties
                Properties settings = new Properties();

                String dbHost = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
                String dbPort = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "5432";
                String dbName = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "javafx_chat";
                String dbUser = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "postgres";
                String dbPass = System.getenv("DB_PASS") != null ? System.getenv("DB_PASS") : "passer";

                String jdbcUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;

                settings.put("hibernate.connection.driver_class", "org.postgresql.Driver");
                settings.put("hibernate.connection.url", jdbcUrl);
                settings.put("hibernate.connection.username", dbUser);
                settings.put("hibernate.connection.password", dbPass);
                settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

                settings.put("hibernate.show_sql", "true");
                settings.put("hibernate.current_session_context_class", "thread");
                settings.put("hibernate.hbm2ddl.auto", "update");

                configuration.setProperties(settings);

                configuration.addAnnotatedClass(User.class);
                configuration.addAnnotatedClass(Message.class);

                ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                        .applySettings(configuration.getProperties()).build();

                sessionFactory = configuration.buildSessionFactory(serviceRegistry);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sessionFactory;
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}
