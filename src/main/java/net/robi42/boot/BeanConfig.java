package net.robi42.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import net.robi42.boot.search.BootElasticsearchProvider;
import net.robi42.boot.search.ElasticsearchEntityMapper;
import net.robi42.boot.search.ElasticsearchProvider;
import net.robi42.boot.util.JerseyApplication;
import net.robi42.boot.util.JerseySwaggerServlet;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.servlets.GzipFilter;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.node.Node;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;

import javax.servlet.Filter;
import javax.ws.rs.client.Client;

import static javax.ws.rs.client.ClientBuilder.newClient;
import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;
import static org.glassfish.jersey.apache.connector.ApacheClientProperties.CONNECTION_MANAGER;
import static org.glassfish.jersey.client.ClientProperties.CONNECT_TIMEOUT;
import static org.glassfish.jersey.client.ClientProperties.READ_TIMEOUT;
import static org.glassfish.jersey.servlet.ServletProperties.JAXRS_APPLICATION_CLASS;

@Configuration
public class BeanConfig {
    @Value("${spring.profiles.active:dev}")
    protected String activeSpringProfiles;
    @Value("${webClient.connectionPool.maxTotal}")
    protected int webClientConnectionPoolMaxTotal;
    @Value("${webClient.connectionPool.defaultMaxPerRoute}")
    protected int webClientConnectionPoolDefaultMaxPerRoute;

    @Bean
    public Filter gzipFilter() {
        return new GzipFilter();
    }

    @Bean
    public ServletRegistrationBean jaxrsServlet() {
        final JerseySwaggerServlet servlet = new JerseySwaggerServlet();
        final ServletRegistrationBean registrationBean = new ServletRegistrationBean(servlet, "/api/*");
        registrationBean.addInitParameter(JAXRS_APPLICATION_CLASS, JerseyApplication.class.getName());
        return registrationBean;
    }

    @Bean
    public ObjectMapper objectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Auto-detect `JSR310Module`...
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false); // -> ISO string serialization
        return objectMapper;
    }

    @Bean
    public JacksonJsonProvider jacksonJsonProvider() {
        return new JacksonJsonProvider(objectMapper());
    }

    @Bean
    public Client webClient() {
        final PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager();
        poolingConnectionManager.setMaxTotal(webClientConnectionPoolMaxTotal);
        poolingConnectionManager.setDefaultMaxPerRoute(webClientConnectionPoolDefaultMaxPerRoute);

        final ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(READ_TIMEOUT, 2000);
        clientConfig.property(CONNECT_TIMEOUT, 500);
        clientConfig.property(CONNECTION_MANAGER, poolingConnectionManager);
        clientConfig.connectorProvider(new ApacheConnectorProvider());

        return newClient(clientConfig)
                .register(jacksonJsonProvider());
    }

    @Bean
    public Node elasticsearchNode() {
        final String settingsResourceName = String.format("elasticsearch%s.yml",
                activeSpringProfiles.equals("test") ? "-test" : "");
        final ImmutableSettings.Builder settingsBuilder = settingsBuilder().loadFromClasspath(settingsResourceName);
        return nodeBuilder().settings(settingsBuilder)
                .node();
    }

    @Bean
    public org.elasticsearch.client.Client elasticsearchClient() {
        return elasticsearchNode().client();
    }

    @Bean
    public ElasticsearchOperations elasticsearchTemplate() {
        return new ElasticsearchTemplate(elasticsearchClient(), new ElasticsearchEntityMapper(objectMapper()));
    }

    @Bean
    public ElasticsearchProvider elasticsearchProvider() {
        return new BootElasticsearchProvider(elasticsearchClient());
    }
}
