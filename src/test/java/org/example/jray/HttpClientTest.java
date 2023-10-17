package org.example.jray;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;

public class HttpClientTest {

    public static void main(String[] args) throws IOException, InterruptedException {
        HttpClient client = HttpClient
                .newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress(1090)))
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://www.baidu.com")).build();
        String body = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        System.out.println(body);

        client.close();
    }

}
