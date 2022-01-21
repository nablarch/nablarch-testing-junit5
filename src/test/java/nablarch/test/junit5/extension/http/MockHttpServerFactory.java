package nablarch.test.junit5.extension.http;

import nablarch.fw.ExecutionContext;
import nablarch.fw.web.HttpRequest;
import nablarch.fw.web.HttpResponse;
import nablarch.fw.web.HttpServer;
import nablarch.fw.web.HttpServerFactory;

/**
 * テスト用の{@link HttpServerFactory}のモック。
 */
public class MockHttpServerFactory implements HttpServerFactory {
    @Override
    public HttpServer create() {
        return new HttpServer() {
            @Override
            public HttpServer start() {
                return null;
            }

            @Override
            public HttpServer startLocal() {
                return null;
            }

            @Override
            public HttpServer join() {
                return null;
            }

            @Override
            public HttpResponse handle(HttpRequest httpRequest, ExecutionContext executionContext) {
                return null;
            }
        };
    }
}
