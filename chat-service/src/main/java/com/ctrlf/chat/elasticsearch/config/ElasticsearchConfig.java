package com.ctrlf.chat.elasticsearch.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 클라이언트 설정
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${app.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUris;

    @Value("${app.elasticsearch.username:}")
    private String username;

    @Value("${app.elasticsearch.password:}")
    private String password;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        // URI 파싱 (예: http://localhost:9200)
        HttpHost httpHost = HttpHost.create(elasticsearchUris);

        // RestClient 빌더 생성 (var 사용으로 타입 추론)
        var builder = org.elasticsearch.client.RestClient.builder(httpHost);

        // 인증 설정 (username/password가 있는 경우)
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(username, password)
            );
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
            );
        }

        org.elasticsearch.client.RestClient restClient = builder.build();

        // ObjectMapper 설정 (JavaTimeModule 추가)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Transport 설정
        ElasticsearchTransport transport = new RestClientTransport(
            restClient,
            new JacksonJsonpMapper(objectMapper)
        );

        // ElasticsearchClient 생성
        return new ElasticsearchClient(transport);
    }
}

