package io.scalecube.gateway.http;

import io.netty.util.concurrent.DefaultThreadFactory;
import io.scalecube.gateway.GatewayMetrics;
import io.scalecube.gateway.GatewayTemplate;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.gateway.GatewayConfig;
import io.scalecube.services.metrics.Metrics;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.resources.LoopResources;

public class HttpGateway extends GatewayTemplate {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpGateway.class);

  private static final DefaultThreadFactory BOSS_THREAD_FACTORY =
      new DefaultThreadFactory("http-boss", true);

  public static final Duration START_TIMEOUT = Duration.ofSeconds(30);

  private DisposableServer server;

  @Override
  public Mono<InetSocketAddress> start(
      GatewayConfig config,
      Executor workerThreadPool,
      boolean preferNative,
      ServiceCall.Call call,
      Metrics metrics) {

    return Mono.defer(
        () -> {
          LOGGER.info("Starting gateway with {}", config);

          GatewayMetrics metrics1 = new GatewayMetrics(config.name(), metrics);
          GatewayHttpAcceptor acceptor = new GatewayHttpAcceptor(call.create(), metrics1);

          LoopResources loopResources =
              prepareLoopResources(preferNative, BOSS_THREAD_FACTORY, config, workerThreadPool);

          server =
              prepareHttpServer(loopResources, metrics1, config.port())
                  .handle(acceptor)
                  .bindNow(START_TIMEOUT);

          InetSocketAddress address = server.address();
          LOGGER.info("Gateway has been started successfully on {}", address);
          return Mono.just(address);
        });
  }

  @Override
  public Mono<Void> stop() {
    return Mono.defer(
        () -> {
          List<Mono<Void>> stopList = new ArrayList<>();
          stopList.add(shutdownBossGroup());
          if (server != null) {
            server.dispose();
            stopList.add(server.onDispose());
          }
          return Mono.when(stopList);
        });
  }
}
