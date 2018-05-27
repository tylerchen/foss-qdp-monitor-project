package com.foreveross.common.restfull.ribbon;

import com.foreveross.common.restfull.RestClient;
import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;

import java.io.IOException;
import java.net.URI;

/**
 * RibbonClient can be used in Feign builder to activate smart routing and resiliency capabilities
 * provided by Ribbon. Ex.
 *
 * <pre>
 * MyService api = Feign.builder.client(RibbonClient.create()).target(MyService.class,
 *     &quot;http://myAppProd&quot;);
 * </pre>
 * <p>
 * Where {@code myAppProd} is the ribbon client name and {@code myAppProd.ribbon.listOfServers}
 * configuration is set.
 */
public class RibbonClient implements RestClient {

    private final RestClient delegate;
    private final LBClientFactory lbClientFactory;

    public static RibbonClient create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    RibbonClient(RestClient delegate, LBClientFactory lbClientFactory) {
        this.delegate = delegate;
        this.lbClientFactory = lbClientFactory;
    }

    @Override
    public Response execute(Request request, Options options) throws IOException {
        try {
            URI asUri = URI.create(request.url());
            String clientName = asUri.getHost();
            URI uriWithoutHost = cleanUrl(request.url(), clientName);
            LBClient.RibbonRequest ribbonRequest = new LBClient.RibbonRequest(delegate, request, uriWithoutHost);
            return lbClient(clientName).executeWithLoadBalancer(ribbonRequest, new OptionsClientConfig(options))
                    .toResponse();
        } catch (ClientException e) {
            propagateFirstIOException(e);
            throw new RuntimeException(e);
        }
    }

    static void propagateFirstIOException(Throwable throwable) throws IOException {
        while (throwable != null) {
            if (throwable instanceof IOException) {
                throw (IOException) throwable;
            }
            throwable = throwable.getCause();
        }
    }

    static URI cleanUrl(String originalUrl, String host) {
        return URI.create(originalUrl.replaceFirst(host, ""));
    }

    private LBClient lbClient(String clientName) {
        return lbClientFactory.create(clientName);
    }

    static class OptionsClientConfig extends DefaultClientConfigImpl {

        public OptionsClientConfig(Options options) {
            setProperty(CommonClientConfigKey.ConnectTimeout, options.connectTimeoutMillis());
            setProperty(CommonClientConfigKey.ReadTimeout, options.readTimeoutMillis());
        }

        @Override
        public void loadProperties(String clientName) {

        }

        @Override
        public void loadDefaultValues() {

        }

    }

    public static final class Builder {

        Builder() {
        }

        private RestClient delegate;
        private LBClientFactory lbClientFactory;

        public Builder delegate(RestClient delegate) {
            this.delegate = delegate;
            return this;
        }

        public Builder lbClientFactory(LBClientFactory lbClientFactory) {
            this.lbClientFactory = lbClientFactory;
            return this;
        }

        public RibbonClient build() {
            return new RibbonClient(delegate != null ? delegate : new DefaultRestClient(null, null),
                    lbClientFactory != null ? lbClientFactory : new LBClientFactory.Default());
        }
    }
}
